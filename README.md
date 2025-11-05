# gretl-gt

## Logging

GRETL routes all log messages through the `ch.so.agi.gretlgt.logging`
package. When the plugin runs inside Gradle builds, log calls are bridged to
Gradle's lifecycle, info, debug and error channels so messages respect the
configured build verbosity. Outside of Gradle (for example in unit tests) the
same abstraction falls back to `java.util.logging` with a configurable
minimum level. Each GRETL step emits a pair of lifecycle messages that frame the
execution, while info/debug provide additional detail and errors forward the
original exception.
