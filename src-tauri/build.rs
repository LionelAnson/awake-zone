fn main() {
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows")
        && std::env::var_os("CARGO_FEATURE_LIVE_CAPTURE").is_some()
    {
        println!("cargo:rerun-if-changed=native");
        cc::Build::new()
            .cpp(true)
            .file("native/live_glass.cpp")
            .include("native/generated")
            .flag("/std:c++20")
            .flag("/EHsc")
            .flag("/utf-8")
            .compile("personal_day_glass");
        for lib in [
            "windowsapp",
            "d3d11",
            "dxgi",
            "d2d1",
            "dxguid",
            "dcomp",
            "user32",
            "gdi32",
        ] {
            println!("cargo:rustc-link-lib={lib}");
        }
    }
    tauri_build::build()
}
