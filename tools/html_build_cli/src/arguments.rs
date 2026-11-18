use serde::Serialize;

#[path = "../../terminal_commands/language.rs"]
mod command_language;

pub fn help() -> std::io::Result<&'static str> {
    command_language::help(include_str!("../../terminal_commands/help/zh/build.txt"),
        include_str!("../../terminal_commands/help/en/build.txt"))
}

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Request {
    pub version: u8,
    pub target: String,
    pub app_name: String,
    pub package_name: String,
    pub version_name: String,
    pub version_code: u32,
    pub entry: String,
    pub files: usize,
    pub total_bytes: u64,
    pub icon_bytes: u64,
}

#[derive(Debug)]
pub struct Options {
    pub input: String,
    pub output: String,
    pub icon: Option<String>,
    pub request: Request,
}

pub fn parse(args: &[String]) -> Result<Options, String> {
    if args.first().map(String::as_str) != Some("html") {
        return Err("用法：build html <项目目录> [选项]；使用 build --help 查看帮助".into());
    }
    let mut options = Options {
        input: String::new(), output: "./dist".into(), icon: None,
        request: Request { version: 1, target: "apk".into(), app_name: String::new(),
            package_name: "com.operit.webapp".into(), version_name: "1.0".into(), version_code: 1,
            entry: "index.html".into(), files: 0, total_bytes: 0, icon_bytes: 0 },
    };
    let mut seen = std::collections::HashSet::new();
    let mut index = 1;
    while index < args.len() {
        let arg = &args[index];
        if !arg.starts_with('-') {
            if !options.input.is_empty() { return Err("只能指定一个项目目录".into()); }
            options.input = arg.clone(); index += 1; continue;
        }
        if !seen.insert(arg.clone()) { return Err(format!("重复选项：{arg}")); }
        let value = args.get(index + 1).filter(|value| !value.is_empty() && !value.starts_with("--"))
            .ok_or_else(|| format!("{arg} 缺少值"))?;
        match arg.as_str() {
            "--target" => options.request.target = match value.as_str() {
                "apk" | "android" => "apk".into(), "exe" | "windows" => "exe".into(),
                _ => return Err(format!("不支持的构建目标：{value}")),
            },
            "--output" => options.output = value.clone(),
            "--entry" => options.request.entry = value.clone(),
            "--name" => options.request.app_name = value.clone(),
            "--package" => options.request.package_name = value.clone(),
            "--version-name" => options.request.version_name = value.clone(),
            "--version-code" => {
                options.request.version_code = value.parse::<u32>().map_err(|_| "版本号必须为正整数")?;
                if options.request.version_code == 0 || options.request.version_code > i32::MAX as u32 {
                    return Err("版本号必须介于 1 和 2147483647 之间".into());
                }
            }
            "--icon" => options.icon = Some(value.clone()),
            _ => return Err(format!("未知选项：{arg}")),
        }
        index += 2;
    }
    if options.input.is_empty() { return Err("缺少 HTML 项目目录".into()); }
    Ok(options)
}

#[cfg(test)]
mod tests {
    use super::*;
    fn args(values: &[&str]) -> Vec<String> { values.iter().map(|value| (*value).into()).collect() }
    #[test]
    fn platform_aliases_and_quoted_paths_keep_their_values() {
        let options = parse(&args(&["html", "/work/my web", "--target", "windows", "--output", "/out/my app", "--name", "演示"])).unwrap();
        assert_eq!(options.input, "/work/my web");
        assert_eq!(options.output, "/out/my app");
        assert_eq!(options.request.target, "exe");
        assert_eq!(options.request.app_name, "演示");
    }
    #[test]
    fn malformed_commands_do_not_start_a_build() {
        for values in [vec!["html"], vec!["apk", "web"], vec!["html", "web", "--target", "ios"],
            vec!["html", "web", "--output"], vec!["html", "web", "extra"], vec!["html", "web", "--version-code", "0"],
            vec!["html", "web", "--target", "apk", "--target", "exe"]] {
            assert!(parse(&args(&values)).is_err(), "{values:?}");
        }
    }
}