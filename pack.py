#!/usr/bin/env python3
"""
Собирает APK из base.apk (результат aapt2 link) + classes.dex.

Важно: начиная с Android 11 resources.arsc обязан быть STORED (не сжат)
и выровнен на 4 байта, иначе установка падает с
"Targeting R+ requires the resources.arsc of installed APKs to be stored
uncompressed and aligned on a 4-byte boundary".
"""
import sys
import zipfile

def main():
    if len(sys.argv) != 4:
        print("usage: pack.py <base.apk> <classes.dex> <out.apk>")
        sys.exit(1)

    base, dex, out = sys.argv[1], sys.argv[2], sys.argv[3]

    with zipfile.ZipFile(base, "r") as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        for item in src.infolist():
            data = src.read(item.filename)
            # Android 11+ требует несжатый resources.arsc
            compress = zipfile.ZIP_STORED if item.filename == "resources.arsc" else zipfile.ZIP_DEFLATED
            dst.writestr(zipfile.ZipInfo(item.filename, item.date_time), data, compress_type=compress)
        dst.writestr("classes.dex", open(dex, "rb").read())

    with zipfile.ZipFile(out) as check:
        print([(n.filename, n.compress_type) for n in check.infolist()])

if __name__ == "__main__":
    main()
