"""Inspect every packaged ELF LOAD alignment, including APK zip data offsets.

Usage: python tools/check-native-alignment.py path/to/app.apk [path/to/app.aab]
The Android 16 KB requirement applies to the 64-bit ARM and x86 libraries.
"""
import json
import struct
import sys
import zipfile


def check(path):
    results = []
    with zipfile.ZipFile(path) as archive, open(path, "rb") as raw:
        for info in archive.infolist():
            if not info.filename.endswith(".so"):
                continue
            data = archive.read(info)
            if data[:4] != b"\x7fELF":
                raise ValueError(f"Not ELF: {info.filename}")
            endian = "<" if data[5] == 1 else ">"
            is64 = data[4] == 2
            offset = struct.unpack_from(endian + ("Q" if is64 else "I"), data, 32 if is64 else 28)[0]
            entry_size, count = struct.unpack_from(endian + "HH", data, 54 if is64 else 42)
            alignments = []
            for index in range(count):
                header = offset + index * entry_size
                if struct.unpack_from(endian + "I", data, header)[0] == 1:
                    alignments.append(struct.unpack_from(endian + ("Q" if is64 else "I"), data, header + (48 if is64 else 28))[0])
            raw.seek(info.header_offset)
            local = raw.read(30)
            name_size, extra_size = struct.unpack_from("<HH", local, 26)
            zip_offset = info.header_offset + 30 + name_size + extra_size
            zip_ok = not path.endswith(".apk") or info.compress_type != zipfile.ZIP_STORED or zip_offset % 16384 == 0
            required = "/arm64-v8a/" in info.filename or "/x86_64/" in info.filename
            elf_ok = bool(alignments) and all(value >= 16384 for value in alignments)
            results.append(dict(library=info.filename, load_alignment=alignments, zip_alignment_ok=zip_ok,
                                required_64_bit=required, passed=(elf_ok and zip_ok) if required else None))
    return dict(artifact=path, libraries=results, passed=all(item["passed"] is not False for item in results))


if __name__ == "__main__":
    reports = [check(path) for path in sys.argv[1:]]
    if not reports:
        raise SystemExit("Provide an APK or AAB path")
    print(json.dumps(reports, indent=2))
    raise SystemExit(0 if all(report["passed"] for report in reports) else 1)
