mod arguments;

use arguments::{Options};
use serde_json::{Value, json};
use std::fs::{self, File, OpenOptions};
use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::Shutdown;
use std::os::linux::net::SocketAddrExt;
use std::os::unix::net::{SocketAddr, UnixStream};
use std::os::unix::ffi::OsStrExt;
use std::path::{Component, Path, PathBuf};
use std::process::ExitCode;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

const MAX_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const MAX_FILES: usize = 20_000;

fn error(message: impl ToString) -> String { message.to_string() }

fn relative_path(value: &str) -> Result<PathBuf, String> {
    let path = Path::new(value);
    if value.is_empty() || value.len() > 2048 || value.contains('\\') || value.contains('\0')
        || value.split('/').count() > 64 || value.split('/').any(|part| part.is_empty() || part == "." || part == "..")
        || !path.components().all(|part| matches!(part, Component::Normal(_))) {
        return Err(format!("不合法的相对路径：{value}"));
    }
    Ok(path.to_path_buf())
}

fn collect(root: &Path, directory: &Path, excluded: &Path, files: &mut Vec<(String, u64)>, bytes: &mut u64) -> Result<(), String> {
    if directory.strip_prefix(root).map_err(error)?.components().count() > 64 { return Err("项目目录超过 64 层".into()); }
    let mut entries = fs::read_dir(directory).map_err(error)?.collect::<Result<Vec<_>, _>>().map_err(error)?;
    entries.sort_by_key(|entry| entry.file_name());
    for entry in entries {
        // 输出目录可位于项目内，但成品和接收临时文件不会再次打包进网页。
        if entry.path() == excluded { continue; }
        let kind = entry.file_type().map_err(error)?;
        if kind.is_symlink() { return Err(format!("项目包含符号链接，请将真实文件放入项目：{}", entry.path().display())); }
        if kind.is_dir() { collect(root, &entry.path(), excluded, files, bytes)?; }
        else if kind.is_file() {
            let name = entry.path().strip_prefix(root).map_err(error)?.to_str().ok_or("项目文件名必须为 UTF-8")?.to_owned();
            relative_path(&name)?;
            let size = entry.metadata().map_err(error)?.len();
            *bytes = bytes.checked_add(size).ok_or("项目大小溢出")?;
            if *bytes > MAX_BYTES || files.len() >= MAX_FILES { return Err("项目超过 2 GiB 或 20000 个文件的构建限制".into()); }
            files.push((name, size));
        } else { return Err(format!("项目中包含非普通文件：{}", entry.path().display())); }
    }
    Ok(())
}

fn write_frame(stream: &mut UnixStream, frame: &impl serde::Serialize) -> Result<(), String> {
    let mut data = serde_json::to_vec(frame).map_err(error)?;
    if data.len() > 65_536 { return Err("构建控制帧过大".into()); }
    data.push(b'\n'); stream.write_all(&data).map_err(error)
}

fn send_file(stream: &mut UnixStream, path: &Path, size: u64) -> Result<(), String> {
    let mut input = File::open(path).map_err(error)?;
    if input.metadata().map_err(error)?.len() != size { return Err(format!("构建期间文件发生变化：{}", path.display())); }
    let written = io::copy(&mut Read::by_ref(&mut input).take(size), stream).map_err(error)?;
    let mut extra = [0u8];
    if written != size || input.read(&mut extra).map_err(error)? != 0 { return Err(format!("构建期间文件发生变化：{}", path.display())); }
    Ok(())
}

fn read_frame(reader: &mut impl BufRead) -> Result<Value, String> {
    let mut line = Vec::new();
    reader.take(65_538).read_until(b'\n', &mut line).map_err(error)?;
    if line.len() > 65_537 || line.last() != Some(&b'\n') { return Err("构建服务返回的消息不完整或过大".into()); }
    let frame: Value = serde_json::from_slice(&line).map_err(error)?;
    if frame["version"] != 1 { return Err("构建命令与应用协议不一致，请重新打开终端更新命令".into()); }
    Ok(frame)
}

fn receive(mut reader: impl BufRead, staging: &Path) -> Result<Option<Vec<String>>, String> {
    let started = Instant::now();
    let mut count = 0;
    let mut directory_count = 0;
    let mut bytes = 0u64;
    loop {
        let frame = read_frame(&mut reader)?;
        match frame["kind"].as_str().ok_or("消息缺少 kind")? {
            "progress" => {
                let message = frame["message"].as_str().ok_or("进度消息缺少文本")?;
                let progress = frame["progress"].as_f64().ok_or("进度值无效")?;
                if !(0.0..=1.0).contains(&progress) { return Err("进度值超出范围".into()); }
                println!("[{:>4}s] [{:>3.0}%] {message}", started.elapsed().as_secs(), progress * 100.0);
                io::stdout().flush().map_err(error)?;
            }
            "directory" => {
                directory_count += 1;
                if directory_count > MAX_FILES { return Err("构建产物目录过多".into()); }
                let name = frame["path"].as_str().ok_or("目录缺少路径")?;
                fs::create_dir_all(staging.join(relative_path(name)?)).map_err(error)?;
            }
            "artifact" => {
                count += 1;
                let name = frame["path"].as_str().ok_or("产物缺少路径")?;
                let size = frame["size"].as_u64().ok_or("产物大小无效")?;
                bytes = bytes.checked_add(size).ok_or("产物大小溢出")?;
                if count > MAX_FILES || bytes > MAX_BYTES { return Err("构建产物超过传输限制".into()); }
                let destination = staging.join(relative_path(name)?);
                fs::create_dir_all(destination.parent().ok_or("产物路径无效")?).map_err(error)?;
                let mut output = OpenOptions::new().write(true).create_new(true).open(&destination).map_err(error)?;
                let mut remaining = size;
                let mut buffer = [0u8; 32 * 1024];
                let mut reported = Instant::now();
                while remaining > 0 {
                    let limit = remaining.min(buffer.len() as u64) as usize;
                    let amount = reader.read(&mut buffer[..limit]).map_err(error)?;
                    if amount == 0 { return Err("产物传输中断".into()); }
                    output.write_all(&buffer[..amount]).map_err(error)?;
                    remaining -= amount as u64;
                    if reported.elapsed() >= Duration::from_secs(1) {
                        println!("[{:>4}s] 正在接收成品 {name}：{}/{size} 字节", started.elapsed().as_secs(), size - remaining);
                        io::stdout().flush().map_err(error)?;
                        reported = Instant::now();
                    }
                }
                output.sync_all().map_err(error)?;
            }
            "result" => {
                if frame["success"] != true {
                    eprintln!("构建失败：{}", frame["message"].as_str().ok_or("失败消息缺少原因")?);
                    return Ok(None);
                }
                if count == 0 { return Err("构建服务未返回产物".into()); }
                let values = frame["outputs"].as_array().ok_or("构建结果缺少输出清单")?;
                let outputs = values.iter().map(|value| {
                    let path = value.as_str().ok_or("输出清单格式无效")?;
                    relative_path(path)?;
                    if !staging.join(path).is_file() { return Err("输出清单中的文件没有接收完成".into()); }
                    Ok(path.to_owned())
                }).collect::<Result<Vec<_>, String>>()?;
                if outputs.is_empty() { return Err("构建输出清单为空".into()); }
                return Ok(Some(outputs));
            }
            other => return Err(format!("未知构建消息：{other}")),
        }
    }
}

// Linux 的原子不覆盖重命名同时保护 APK 和完整的 Windows 目录，避免检查后被其他任务抢占。
fn publish(source: &Path, destination: &Path) -> Result<(), String> {
    unsafe extern "C" {
        fn renameat2(old_directory: std::ffi::c_int, old_path: *const std::ffi::c_char,
            new_directory: std::ffi::c_int, new_path: *const std::ffi::c_char, flags: std::ffi::c_uint) -> std::ffi::c_int;
    }
    let source = std::ffi::CString::new(source.as_os_str().as_bytes()).map_err(error)?;
    let destination = std::ffi::CString::new(destination.as_os_str().as_bytes()).map_err(error)?;
    // 两个绝对路径都保存在当前接收目录所在文件系统；RENAME_NOREPLACE 不会替换既有文件或目录。
    let result = unsafe { renameat2(-100, source.as_ptr(), -100, destination.as_ptr(), 1) };
    if result != 0 { return Err(format!("无法发布构建产物：{}", io::Error::last_os_error())); }
    Ok(())
}

struct Temporary(PathBuf);
impl Drop for Temporary {
    fn drop(&mut self) {
        if let Err(failure) = fs::remove_dir_all(&self.0) { eprintln!("清理构建接收目录失败：{failure}"); }
    }
}

fn run(mut options: Options) -> Result<bool, String> {
    println!("正在准备 HTML 构建，扫描项目文件……");
    io::stdout().flush().map_err(error)?;
    let root = fs::canonicalize(&options.input).map_err(error)?;
    if !root.is_dir() { return Err("输入必须是项目目录".into()); }
    if options.request.app_name.is_empty() {
        options.request.app_name = root.file_name().and_then(|name| name.to_str()).ok_or("请用 --name 指定应用名称")?.into();
    }
    let name = &options.request.app_name;
    if name.is_empty() || name.trim() != name || name.ends_with('.') || name.chars().any(|ch| ch.is_control() || "<>:\"/\\|?*".contains(ch)) {
        return Err("应用名称包含无效文件名字符".into());
    }
    let entry = relative_path(&options.request.entry)?;
    if !root.join(entry).is_file() { return Err("入口 HTML 不存在，请使用 --entry 指定项目内的入口".into()); }
    fs::create_dir_all(&options.output).map_err(error)?;
    let output = fs::canonicalize(&options.output).map_err(error)?;
    if output == root { return Err("输出目录不能与输入项目目录相同".into()); }
    let final_name = if options.request.target == "apk" { format!("{name}.apk") } else { name.clone() };
    if output.join(&final_name).exists() { return Err(format!("产物已存在，请更换输出目录或移走旧产物：{}", output.join(&final_name).display())); }
    let mut files = Vec::new();
    let mut bytes = 0;
    collect(&root, &root, &output, &mut files, &mut bytes)?;
    if !files.iter().any(|(path, _)| path == &options.request.entry) { return Err("入口 HTML 位于已排除的输出目录内".into()); }
    options.request.files = files.len(); options.request.total_bytes = bytes;
    let icon = options.icon.map(fs::canonicalize).transpose().map_err(error)?;
    if let Some(path) = &icon {
        let metadata = fs::metadata(path).map_err(error)?;
        if !metadata.is_file() || metadata.len() == 0 || metadata.len() > 16 * 1024 * 1024 { return Err("图标必须是 16 MiB 以内的非空文件".into()); }
        options.request.icon_bytes = metadata.len();
    }
    let channel = std::env::var("OPERIT_BUILD_SOCKET").map_err(|_| "请从 Operit 内置终端使用 build；当前缺少构建服务通道")?;
    let address = SocketAddr::from_abstract_name(channel.as_bytes()).map_err(error)?;
    let mut stream = UnixStream::connect_addr(&address).map_err(|failure| format!("无法连接应用构建服务，请重新打开终端：{failure}"))?;
    stream.set_read_timeout(Some(Duration::from_secs(120))).map_err(error)?;
    stream.set_write_timeout(Some(Duration::from_secs(120))).map_err(error)?;
    let unique = SystemTime::now().duration_since(UNIX_EPOCH).map_err(error)?.as_nanos();
    let staging = Temporary(output.join(format!(".operit-build-{}-{unique}", std::process::id())));
    fs::create_dir(&staging.0).map_err(error)?;
    write_frame(&mut stream, &options.request)?;
    let receiver = BufReader::new(stream.try_clone().map_err(error)?);
    let destination = staging.0.clone();
    let handle = std::thread::spawn(move || receive(receiver, &destination));
    let upload = (|| {
        for (name, size) in files {
            write_frame(&mut stream, &json!({"path": &name, "size": size}))?;
            send_file(&mut stream, &root.join(&name), size)?;
        }
        if let Some(path) = icon { send_file(&mut stream, &path, options.request.icon_bytes)?; }
        stream.shutdown(Shutdown::Write).map_err(error)
    })();
    if upload.is_err() { let _ = stream.shutdown(Shutdown::Write); }
    let received = handle.join().map_err(|_| "构建接收线程异常")??;
    let Some(outputs) = received else { return Ok(false); };
    upload?;
    let roots = fs::read_dir(&staging.0).map_err(error)?.collect::<Result<Vec<_>, _>>().map_err(error)?;
    if roots.len() != 1 || roots[0].file_name().as_os_str() != std::ffi::OsStr::new(&final_name) { return Err("构建产物与请求的应用名称不一致".into()); }
    if output.join(&final_name).exists() { return Err("接收产物时目标名称已被其他任务占用".into()); }
    publish(&roots[0].path(), &output.join(&final_name))?;
    for path in outputs { println!("构建完成：{}", output.join(path).display()); }
    Ok(true)
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.is_empty() || args == ["--help"] || args == ["-h"] || args == ["html", "--help"] || args == ["html", "-h"] {
        return match arguments::help() {
            Ok(help) => { print!("{help}"); ExitCode::SUCCESS }
            Err(error) => { eprintln!("{error}"); ExitCode::FAILURE }
        };
    }
    if args == ["--version"] { println!("build {}", env!("CARGO_PKG_VERSION")); return ExitCode::SUCCESS; }
    let options = match arguments::parse(&args) { Ok(value) => value, Err(failure) => { eprintln!("{failure}"); return ExitCode::from(2); } };
    match run(options) { Ok(true) => ExitCode::SUCCESS, Ok(false) => ExitCode::FAILURE, Err(failure) => { eprintln!("构建失败：{failure}"); ExitCode::FAILURE } }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn unsafe_artifact_names_are_rejected_before_creating_files() {
        for path in ["../outside", "/absolute", "a/../../b", "a\\b", "", "./a", "a/./b", "a//b", "a/"] { assert!(relative_path(path).is_err(), "{path}"); }
        assert!(relative_path(&vec!["a"; 65].join("/")).is_err());
        assert!(relative_path(&"x".repeat(2049)).is_err());
        assert_eq!(relative_path("项目/assets/main.js").unwrap(), PathBuf::from("项目/assets/main.js"));
    }
    #[test]
    fn incomplete_and_incompatible_frames_fail() {
        assert!(read_frame(&mut io::Cursor::new(b"{\"version\":1}")).is_err());
        assert!(read_frame(&mut io::Cursor::new(b"{\"version\":2}\n")).is_err());
        assert!(read_frame(&mut io::Cursor::new(vec![b'x'; 70_000])).is_err());
    }
}
