#!/usr/bin/env bash
# Runs the instrumented tests (app/src/androidTest) on ONE emulator.
#
# A connected test run installs the app on the device, clears its data before each test and
# uninstalls it at the end – on a phone in use that would remove Pickelhaube with its boxes and
# passwords. So this refuses anything but an emulator: pass its serial, or start exactly one.
#
#   scripts/instrumented-tests.sh                 # the only emulator running
#   scripts/instrumented-tests.sh emulator-5556   # a given one
#   scripts/instrumented-tests.sh emulator-5556 SpeedDialFlowTest   # one test class (or Class#method)
set -euo pipefail
cd "$(dirname "$0")/.."

sdk=$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)
adb=${ADB:-${sdk:+$sdk/platform-tools/}adb}

serial=${1:-}
only=${2:-}
if [[ -z $serial ]]; then
    mapfile -t emulators < <("$adb" devices | awk '$2 == "device" && $1 ~ /^emulator-/ { print $1 }')
    if (( ${#emulators[@]} != 1 )); then
        echo "Start exactly one emulator, or name one: $0 emulator-5554 (running: ${emulators[*]:-none})" >&2
        exit 1
    fi
    serial=${emulators[0]}
fi
if [[ $serial != emulator-* ]]; then
    echo "Not an emulator: $serial – the tests would remove the app and its data there." >&2
    exit 1
fi

# A serial that is not attached would leave Gradle to pick the devices itself – the phone among them.
if ! "$adb" devices | awk -v s="$serial" '$1 == s && $2 == "device" { found = 1 } END { exit !found }'; then
    echo "Not running: $serial is not attached ($("$adb" devices | awk 'NR > 1 && $2 == "device" { printf "%s ", $1 }'))." >&2
    exit 1
fi

echo "Instrumented tests on $serial (Android $("$adb" -s "$serial" shell getprop ro.build.version.release | tr -d '\r'))"
results=app/build/outputs/androidTest-results/connected/debug/flavors/full
rm -rf "$results"
status=0
ANDROID_SERIAL=$serial ./gradlew connectedFullDebugAndroidTest \
    ${only:+-Pandroid.testInstrumentationRunnerArguments.class=org.rustygnome.pickelhaube.$only} || status=$?

# Gradle may report success for a run that broke off (e.g. the emulator went away): check the results.
summary=$(cat "$results"/TEST-*.xml 2>/dev/null | grep -o '<testsuites [^>]*>' || true)
echo "${summary:-No test results in $results}"
if [[ -z $summary || $summary != *'failures="0"'* || $summary != *'errors="0"'* ]]; then
    (( status != 0 )) || status=1
fi
exit "$status"
