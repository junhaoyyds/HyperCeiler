#!/usr/bin/env python3
"""Repack an APK so that native libraries (.so) are forced STORED + 4KB
page-aligned, inject the desktop dock hook registration, and drop stale v1
signatures so jarsigner can re-sign.

Pure-python, no external zipalign/apksigner required.

Why STORED + 4KB-aligned .so is mandatory:
  On modern Android (extractNativeLibs defaults to false), the package
  manager mmaps .so directly from the APK. That requires the .so entry to be
  stored uncompressed (STORED) AND 4KB page-aligned (Android extra field
  0xD935). Otherwise installation fails with:
      INSTALL_FAILED_INVALID_APK: Failed to extract native libraries, res=-2
"""
import struct, sys, os, zipfile, zlib

PAGE = 4096
MIN_ALIGN = 4
ALIGN_MARKER = 0xD935  # Android zip-alignment extra field id


def dos_time(dt):
    h, m, s = dt[3], dt[4], dt[5]
    return (h << 11) | (m << 5) | (s // 2)


def dos_date(dt):
    y, mo, d = dt[0], dt[1], dt[2]
    return ((y - 1980) << 9) | (mo << 5) | d


def strip_align_marker(extra):
    """Remove any existing 0xD935 alignment records so we recompute against the
    new layout (stale offsets from the source would be wrong)."""
    tag = ALIGN_MARKER.to_bytes(2, "little")
    if tag not in extra:
        return extra
    out = b""
    i = 0
    while i + 4 <= len(extra):
        hid = struct.unpack_from("<H", extra, i)[0]
        hsz = struct.unpack_from("<H", extra, i + 2)[0]
        if hid == ALIGN_MARKER:
            i += 4 + hsz
            continue
        out += extra[i:i + 4 + hsz]
        i += 4 + hsz
    return out


def repack(src_path, dst_path, native_init=None):
    src = zipfile.ZipFile(src_path)
    infos = src.infolist()
    # Decompressed bytes for every entry (what a v1 signature covers).
    plain = {it.filename: src.read(it.filename) for it in infos}
    src.close()

    # Drop stale v1 signatures so jarsigner regenerates them cleanly.
    skip = set()
    for n in list(plain.keys()):
        if n.startswith("META-INF/"):
            b = n.rsplit("/", 1)[-1]
            if b.endswith((".SF", ".RSA", ".DSA", ".EC")) or b == "MANIFEST.MF":
                skip.add(n)

    out = open(dst_path, "wb")
    central = []
    pos = 0  # absolute offset of current write position

    for it in infos:
        name = it.filename
        if name in skip:
            continue
        pdata = plain[name]
        is_so = name.endswith(".so")
        if is_so:
            # FORCE STORED + 4KB page-aligned, independent of source compression.
            ctype = zipfile.ZIP_STORED
            align = PAGE
            wbytes = pdata  # pdata is the correct (decompressed) content
            csize = len(wbytes)
        else:
            ctype = it.compress_type
            align = MIN_ALIGN if ctype == zipfile.ZIP_STORED else 1
            if ctype == zipfile.ZIP_DEFLATED:
                co = zlib.compressobj(9, zlib.DEFLATED, -15)  # raw deflate
                wbytes = co.compress(pdata) + co.flush()
                csize = len(wbytes)
            else:
                wbytes = pdata
                csize = len(wbytes)
        usize = len(pdata)
        crc = it.CRC

        name_b = name.encode("utf-8")
        extra = strip_align_marker(it.extra)
        if align > 1:
            pre = pos + 30 + len(name_b) + len(extra)
            pad = (align - ((pre + 4) % align)) % align
            rec = struct.pack("<HH", ALIGN_MARKER, pad) + (b"\x00" * pad)
            extra = extra + rec

        local_offset = pos
        flags = it.flag_bits & ~0x08  # drop data-descriptor bit; sizes in header
        lh = struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50,            # local file header signature
            it.extract_version,
            flags,
            ctype,
            dos_time(it.date_time),
            dos_date(it.date_time),
            crc,
            csize,
            usize,
            len(name_b),
            len(extra),
        )
        out.write(lh)
        out.write(name_b)
        out.write(extra)
        out.write(wbytes)
        pos = out.tell()

        cd = struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50,            # central dir signature
            0x031E,                # version made by
            it.extract_version,    # version needed
            flags,
            ctype,
            dos_time(it.date_time),
            dos_date(it.date_time),
            crc,
            csize,
            usize,
            len(name_b),
            len(extra),
            0,                     # comment length
            0,                     # disk number start
            it.internal_attr,
            it.external_attr,
            local_offset,          # offset of local header
        )
        cd += name_b + extra
        central.append(cd)

    # Inject desktop dock hook registration (STORED, 4-byte aligned).
    if native_init is not None and native_init[0] not in plain:
        name = native_init[0]
        pdata = native_init[1]
        name_b = name.encode("utf-8")
        pre = pos + 30 + len(name_b) + 0
        pad = (MIN_ALIGN - ((pre + 4) % MIN_ALIGN)) % MIN_ALIGN
        rec = struct.pack("<HH", ALIGN_MARKER, pad) + (b"\x00" * pad)
        extra = rec
        local_offset = pos
        crc = zlib.crc32(pdata) & 0xFFFFFFFF
        usize = csize = len(pdata)
        ctype = zipfile.ZIP_STORED
        flags = 0
        lh = struct.pack(
            "<IHHHHHIIIHH",
            0x04034B50, 0x031E, flags, ctype, 0, 0x21,  # date 1980-01-01
            crc, csize, usize, len(name_b), len(extra),
        )
        out.write(lh)
        out.write(name_b)
        out.write(extra)
        out.write(pdata)
        pos = out.tell()
        cd = struct.pack(
            "<IHHHHHHIIIHHHHHII",
            0x02014B50, 0x031E, 0x031E, flags, ctype, 0, 0x21,
            crc, csize, usize, len(name_b), len(extra),
            0, 0, 0, 0, local_offset,
        )
        cd += name_b + extra
        central.append(cd)

    cd_offset = out.tell()
    cd_size = sum(len(c) for c in central)
    cd_count = len(central)
    eocd = struct.pack(
        "<IHHHHIIH",
        0x06054B50, 0, 0, cd_count, cd_count, cd_size, cd_offset, 0,
    )
    for c in central:
        out.write(c)
    out.write(eocd)
    out.close()


if __name__ == "__main__":
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else (src + ".aligned")
    repack(src, dst, native_init=("META-INF/xposed/native_init.list", b"libhyperceiler_home.so\n"))
    print("ALIGNED ->", dst, os.path.getsize(dst), "bytes")
