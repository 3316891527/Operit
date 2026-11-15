# 归档设备回归样例

`rar5-stored.rar.base64` 来自 libarchive 的 `libarchive/test/test_read_format_rar5_stored.rar.uu`，以 Base64 保存原始 RAR5 字节；包含 `helloworld.txt`，内容为 `hello libarchive test suite!` 与末尾换行。

来源：https://github.com/libarchive/libarchive/blob/master/libarchive/test/test_read_format_rar5_stored.rar.uu

该样例遵循 libarchive BSD 许可，许可全文随应用保存在 `app/src/main/assets/licenses/libarchive-core-LICENSE.txt`。

两个 `sample.tar.*.base64` 由 Python 标准库从固定路径、固定时间戳和固定 UTF-8 文本生成，验证 Bzip2 与 XZ 过滤器以及中文路径。测试运行时解码到独立缓存目录，不修改设备已有文件。
