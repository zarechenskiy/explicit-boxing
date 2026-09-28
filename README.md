# explicit-boxing

![Build](https://github.com/zarechenskiy/explicit-boxing/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

An IntelliJ IDEA plugin that reveals implicit boxing in Kotlin code.

The plugin reads the **compiled bytecode** of the Kotlin file open in the editor and puts a gutter icon on every line
where a value gets boxed. Hovering the icon shows what exactly is boxed:

- primitives boxed into wrappers, e.g. `Int → java.lang.Integer` (`Integer.valueOf`, or `Boxing.boxInt` inside suspend functions);
- value classes boxed via the synthetic `box-impl` method, e.g. `value class UserId (over Int)`.

Boxing that comes from inlined functions is attributed to the call site, using the SMAP debug info emitted by the Kotlin compiler.

### How it works

- Class files are looked up in the module compiler output and in conventional build output directories
  (`build/classes/kotlin/*`, `target/classes`, `out/production/*`) by package and the `SourceFile` attribute.
- Gutter icons are refreshed after every build. If the source was changed after the last build,
  the icon is greyed out and the tooltip warns that the bytecode may be outdated.

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "explicit-boxing"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/zarechenskiy/explicit-boxing/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
