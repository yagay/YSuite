#!/usr/bin/env python3
"""Audit ELF PT_LOAD alignment and APK ZIP offsets for 16 KiB page support."""
import argparse
import struct
import sys
import zipfile
from pathlib import Path

PAGE = 16384
PT_LOAD = 1

def elf_alignment(data):
    if len(data) < 64 or data[:4] != b"\x7fELF":
        return None, "not ELF"
    if data[4] != 2 or data[5] != 1:
        return None, "unsupported ELF class/endianness"
    try:
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phentsize = struct.unpack_from("<H", data, 54)[0]
        phnum = struct.unpack_from("<H", data, 56)[0]
        aligns = []
        if phentsize < 56 or phnum == 0 or phoff + phentsize * phnum > len(data):
            return None, "invalid program header table"
        for index in range(phnum):
            pos = phoff + phentsize * index
            typ = struct.unpack_from("<I", data, pos)[0]
            if typ == PT_LOAD:
                offset = struct.unpack_from("<Q", data, pos + 8)[0]
                vaddr = struct.unpack_from("<Q", data, pos + 16)[0]
                align = struct.unpack_from("<Q", data, pos + 48)[0]
                aligns.append(align)
                if align < PAGE or (offset - vaddr) % PAGE:
                    return False, f"PT_LOAD[{index}] align={align} offset={offset} vaddr={vaddr}"
        if not aligns:
            return None, "no PT_LOAD segment"
        return True, f"minimum PT_LOAD p_align={min(aligns)}"
    except struct.error as error:
        return None, f"malformed ELF: {error}"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()
    failed = 0
    unknown = 0
    with zipfile.ZipFile(args.apk) as zf:
        for info in sorted(zf.infolist(), key=lambda x: x.filename):
            if not (info.filename.startswith("lib/") and info.filename.endswith(".so")):
                continue
            ok, detail = elf_alignment(zf.read(info))
            # The ZIP local header size determines the uncompressed ELF's actual offset.
            zip_offset = None
            with args.apk.open("rb") as fp:
                fp.seek(info.header_offset)
                header = fp.read(30)
                if len(header) == 30 and header[:4] == b"PK\x03\x04":
                    name_len, extra_len = struct.unpack_from("<HH", header, 26)
                    zip_offset = info.header_offset + 30 + name_len + extra_len
            zip_issue = (info.compress_type == zipfile.ZIP_STORED and
                         (zip_offset is None or zip_offset % PAGE != 0))
            if ok is False or zip_issue:
                failed += 1
                status = "FAIL"
            elif ok is None:
                unknown += 1
                status = "UNKNOWN"
            else:
                status = "OK"
            extra = f"; uncompressed ZIP offset={zip_offset}" if info.compress_type == zipfile.ZIP_STORED else "; compressed in APK"
            print(f"{status}: {info.filename}: {detail}{extra}")
    print(f"16 KiB native audit: failed={failed}, unknown={unknown}")
    if args.strict and (failed or unknown):
        return 1
    return 0

if __name__ == "__main__":
    sys.exit(main())
