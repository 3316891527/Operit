mod check;
mod options;
mod output;

use codex_exec_server::ExecutorFileSystem;
use codex_utils_absolute_path::AbsolutePathBuf;
use codex_utils_path_uri::PathUri;
use options::Options;
use output::{VERSION, document, emit_json, failure};
use std::io::{self, Read, Write};

const HELP: &str = r#"Usage: apply_patch [OPTIONS] [PATCH]
       apply_patch [OPTIONS] --file PATH
       apply_patch [OPTIONS] < patch.txt

Apply a Codex-format patch. Relative paths use the current directory.

Options:
  -v, -V, --version  Print the Operit command version
  -h, --help        Show this help
  -c, --check       Check sequential patch operations without writing files
      --dry-run     Alias for --check
  -f, --file PATH   Read UTF-8 patch text from PATH; '-' means standard input
      --json        Print one JSON result object, including errors
      --            End option parsing; the following argument is PATCH

Patch format:
  *** Begin Patch
  *** Add File: example.txt
  +hello
  *** Update File: example.txt
  @@
  -hello
  +world
  *** End Patch

Delete a file with '*** Delete File: path'.
Move a file with '*** Move to: new/path' after an Update File header.
Quote heredoc delimiters (<<'PATCH') to preserve shell characters.
Check mode evaluates content and paths in memory; permissions, disk space
and concurrent changes still need to be resolved by the actual apply.
Exit codes: 0 = success, 1 = patch/I/O error, 2 = usage error.
"#;

pub fn main() -> ! {
    std::process::exit(run());
}

fn run() -> i32 {
    let args = std::env::args_os().skip(1).collect();
    let options = match Options::parse(args) {
        Ok(options) => options,
        Err((json, message)) => {
            return failure(json, "usage", 2, "usage", &format!("Error: {message}"));
        }
    };
    if options.help || options.version {
        let mode = if options.help { "help" } else { "version" };
        let message = if options.help {
            HELP.to_owned()
        } else {
            format!("apply_patch {VERSION}\n")
        };
        if options.json {
            return emit_json(&document(mode, 0, &message, None, None), 0);
        }
        print!("{message}");
        return 0;
    }
    let mode = if options.check { "check" } else { "apply" };
    let patch = if let Some(argument) = options.patch {
        match argument.into_string() {
            Ok(patch) => patch,
            Err(_) => {
                return failure(
                    options.json,
                    mode,
                    1,
                    "input",
                    "Error: apply_patch requires a UTF-8 PATCH argument.",
                );
            }
        }
    } else if let Some(path) = options.file.filter(|path| path.as_os_str() != "-") {
        match std::fs::read_to_string(&path) {
            Ok(patch) => patch,
            Err(error) => {
                return failure(
                    options.json,
                    mode,
                    1,
                    "input",
                    &format!(
                        "Error: Failed to read PATCH file {}.\n{error}",
                        path.display()
                    ),
                );
            }
        }
    } else {
        let mut patch = String::new();
        if let Err(error) = io::stdin().read_to_string(&mut patch) {
            return failure(
                options.json,
                mode,
                1,
                "input",
                &format!("Error: Failed to read PATCH from stdin.\n{error}"),
            );
        }
        if patch.is_empty() {
            return failure(
                options.json,
                mode,
                2,
                "usage",
                "Usage: apply_patch 'PATCH'\n       echo 'PATCH' | apply_patch",
            );
        }
        patch
    };
    let cwd = match AbsolutePathBuf::current_dir() {
        Ok(cwd) => PathUri::from_abs_path(&cwd),
        Err(error) => {
            return failure(
                options.json,
                mode,
                1,
                "io",
                &format!("Error: Failed to determine current directory.\n{error}"),
            );
        }
    };
    let runtime = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(runtime) => runtime,
        Err(error) => {
            return failure(
                options.json,
                mode,
                1,
                "io",
                &format!("Error: Failed to initialize runtime.\n{error}"),
            );
        }
    };
    let check_fs = check::CheckFileSystem::default();
    let filesystem: &dyn ExecutorFileSystem = if options.check {
        &check_fs
    } else {
        codex_exec_server::LOCAL_FS.as_ref()
    };
    let mut out = Vec::new();
    let mut err = Vec::new();
    let result = runtime.block_on(crate::apply_patch(
        &patch, &cwd, &mut out, &mut err, filesystem, None,
    ));
    let (code, delta) = match &result {
        Ok(delta) => (0, delta),
        Err(error) => (1, error.delta()),
    };
    if options.check && code == 0 {
        out = String::from_utf8_lossy(&out)
            .replacen(
                "Success. Updated the following files:",
                "Success. Checked the following files (no files written):",
                1,
            )
            .into_bytes();
    }
    if options.json {
        let message = String::from_utf8_lossy(if code == 0 { &out } else { &err });
        let mut value = document(
            mode,
            code,
            message.trim_end(),
            if code == 0 { None } else { Some("patch") },
            Some(delta),
        );
        if code == 0 {
            value["changes"] = serde_json::json!(output::completed_changes(&patch, &cwd));
        }
        return emit_json(&value, code);
    }
    if let Err(error) = io::stdout()
        .write_all(&out)
        .and_then(|_| io::stdout().flush())
        .and_then(|_| io::stderr().write_all(&err))
    {
        eprintln!("Error: Failed to write result.\n{error}");
        return 1;
    }
    code
}
