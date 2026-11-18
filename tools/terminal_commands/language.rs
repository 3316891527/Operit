use std::io;

pub fn help<'a>(chinese: &'a str, english: &'a str) -> io::Result<&'a str> {
    let language = match std::env::var_os("OPERIT_COMMAND_LANGUAGE_FILE") {
        Some(path) => std::fs::read_to_string(path)?,
        None => std::env::var("OPERIT_COMMAND_LANGUAGE").unwrap_or_else(|_| "en".to_owned()),
    };
    Ok(if is_chinese(&language) { chinese } else { english })
}

fn is_chinese(language: &str) -> bool {
    language.trim().split(['-', '_', '.']).next().is_some_and(|value| value.eq_ignore_ascii_case("zh"))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn chinese_variants_select_chinese_and_other_languages_use_english() {
        for language in ["zh", "zh-CN", "zh-TW", "zh_CN.UTF-8", "ZH-hans"] { assert!(is_chinese(language)); }
        for language in ["en", "ja", "ko", "es", "pt-BR", "", "auto"] { assert!(!is_chinese(language)); }
    }
}
