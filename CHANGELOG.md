<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# explicit-boxing Changelog

## [Unreleased]
### Added
- Gutter icons for lines where implicit boxing happens, based on the compiled bytecode:
  primitive boxing (`Int` → `java.lang.Integer`, …) and value class boxing (`box-impl`)
- Boxing in inlined code is attributed to the call site
