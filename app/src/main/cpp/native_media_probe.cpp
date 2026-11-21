#include <cctype>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#if defined(__ANDROID__)
#include <jni.h>
#else
#include <fstream>
#include <iostream>
#endif

namespace {

bool starts_with(const unsigned char* data, std::size_t size,
                 const unsigned char* signature, std::size_t signature_size) {
    return size >= signature_size &&
           std::memcmp(data, signature, signature_size) == 0;
}

bool has_valid_iso_bmff_box(const unsigned char* data, std::size_t size,
                            const char* type) {
    if (data == nullptr || size < 8) return false;
    const std::uint32_t box_size =
            (static_cast<std::uint32_t>(data[0]) << 24) |
            (static_cast<std::uint32_t>(data[1]) << 16) |
            (static_cast<std::uint32_t>(data[2]) << 8) |
            static_cast<std::uint32_t>(data[3]);
    if (box_size != 0 && box_size < 8) return false;
    if (box_size == 1) {
        if (size < 16) return false;
        const std::uint64_t extended_size =
                (static_cast<std::uint64_t>(data[8]) << 56) |
                (static_cast<std::uint64_t>(data[9]) << 48) |
                (static_cast<std::uint64_t>(data[10]) << 40) |
                (static_cast<std::uint64_t>(data[11]) << 32) |
                (static_cast<std::uint64_t>(data[12]) << 24) |
                (static_cast<std::uint64_t>(data[13]) << 16) |
                (static_cast<std::uint64_t>(data[14]) << 8) |
                static_cast<std::uint64_t>(data[15]);
        if (extended_size < 16) return false;
    }
    return std::memcmp(data + 4, type, 4) == 0;
}

std::string detect_container(const unsigned char* data, std::size_t size) {
    if (data == nullptr || size == 0) return "unknown";
    if (has_valid_iso_bmff_box(data, size, "styp") ||
        has_valid_iso_bmff_box(data, size, "moof")) return "mp4-fragment";
    if (has_valid_iso_bmff_box(data, size, "ftyp")) return "mp4";

    static const unsigned char ebml[] = {0x1A, 0x45, 0xDF, 0xA3};
    if (starts_with(data, size, ebml, sizeof(ebml))) return "webm";

    static const unsigned char png[] = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    if (starts_with(data, size, png, sizeof(png))) return "png";
    static const unsigned char jpeg[] = {0xFF, 0xD8, 0xFF};
    if (starts_with(data, size, jpeg, sizeof(jpeg))) return "jpeg";
    if (size >= 6 && (std::memcmp(data, "GIF87a", 6) == 0 ||
                      std::memcmp(data, "GIF89a", 6) == 0)) return "gif";
    if (size >= 4 && std::memcmp(data, "fLaC", 4) == 0) return "flac";
    if (size >= 4 && std::memcmp(data, "OggS", 4) == 0) return "ogg";
    if (size >= 12 && std::memcmp(data, "RIFF", 4) == 0 &&
                      std::memcmp(data + 8, "WAVE", 4) == 0) return "wav";
    if (size >= 3 && std::memcmp(data, "ID3", 3) == 0) return "mp3";
    if (size >= 2 && data[0] == 0xFF && (data[1] & 0xE0) == 0xE0) return "mp3";
    if (size >= 377 && data[0] == 0x47 &&
        data[188] == 0x47 && data[376] == 0x47) return "mpeg-ts";

    std::string prefix;
    prefix.reserve(size);
    for (std::size_t index = 0; index < size; ++index) {
        prefix.push_back(static_cast<char>(
                std::tolower(static_cast<unsigned char>(data[index]))));
    }
    std::size_t first = 0;
    while (first < prefix.size() &&
           std::isspace(static_cast<unsigned char>(prefix[first]))) {
        ++first;
    }
    if (prefix.compare(first, 9, "<!doctype") == 0 ||
        prefix.compare(first, 5, "<html") == 0 ||
        prefix.compare(first, 5, "<head") == 0 ||
        prefix.compare(first, 5, "<body") == 0) return "html";

    return "unknown";
}

}  // namespace

#if defined(__ANDROID__)
extern "C" JNIEXPORT jstring JNICALL
Java_com_sxsbsxs_filesniffing_NativeMediaProbe_detectContainer(
        JNIEnv* env, jobject /* receiver */, jbyteArray input) {
    if (input == nullptr) return env->NewStringUTF("unknown");
    const jsize length = env->GetArrayLength(input);
    std::vector<unsigned char> bytes(static_cast<std::size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(input, 0, length,
                                reinterpret_cast<jbyte*>(bytes.data()));
    }
    const std::string result = detect_container(bytes.data(), bytes.size());
    return env->NewStringUTF(result.c_str());
}
#else
int main(int argc, char** argv) {
    if (argc != 2) {
        std::cerr << "usage: media_probe <file>\n";
        return 2;
    }
    std::ifstream file(argv[1], std::ios::binary);
    if (!file) {
        std::cerr << "cannot open input file\n";
        return 2;
    }
    std::vector<unsigned char> bytes(4096);
    file.read(reinterpret_cast<char*>(bytes.data()),
              static_cast<std::streamsize>(bytes.size()));
    bytes.resize(static_cast<std::size_t>(file.gcount()));
    std::cout << detect_container(bytes.data(), bytes.size()) << '\n';
    return 0;
}
#endif
