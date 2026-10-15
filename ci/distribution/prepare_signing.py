"""从 GitHub Actions Secret 准备独立发行版的固定签名密钥。"""

import base64
import binascii
import os
from pathlib import Path


def main():
    encoded = os.environ["OPERIT_DISTRIBUTION_KEYSTORE_BASE64"]
    if not encoded:
        raise SystemExit("缺少发行签名密钥 Secret")
    try:
        key_bytes = base64.b64decode(encoded, validate=True)
    except binascii.Error as error:
        raise SystemExit("发行签名密钥 Secret 不是有效的 Base64") from error
    if not key_bytes:
        raise SystemExit("发行签名密钥为空")

    directory = Path(__file__).resolve().parents[2] / ".ci-signing"
    directory.mkdir(mode=0o700, exist_ok=True)
    target = directory / "operit-distribution.p12"
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(key_bytes)
    print("独立发行签名密钥已准备")


if __name__ == "__main__":
    main()
