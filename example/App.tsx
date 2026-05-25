/**
 * react-native-alarm — example app
 *
 * SOUND SETUP (required before device run):
 *   Android: place example/android/app/src/main/res/raw/demo.mp3
 *   iOS:     add demo.wav (or demo.caf) to the Xcode app target bundle
 * These binary files are not committed — add them yourself before running on device.
 *
 * The import below resolves via metro.config.js extraNodeModules alias and
 * tsconfig paths: "react-native-alarm" → the parent package root.
 */
import {
  addAlarmEventListener,
  cancelAlarm,
  cancelAll,
  getAlarmHealth,
  isAvailable,
  openBatteryOptimizationSettings,
  openExactAlarmSettings,
  openNotificationSettings,
  requestPermissions,
  scheduleAlarm,
  stopSound,
  type AlarmEvent,
  type AlarmHealth,
} from 'react-native-alarm';
import React, { useEffect, useRef, useState } from 'react';
import {
  Button,
  Platform,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

type Status =
  | { kind: 'idle' }
  | { kind: 'ok'; message: string }
  | { kind: 'error'; message: string };

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function formatHealth(h: AlarmHealth): string {
  return [
    `notifications: ${h.notificationsEnabled}`,
    `exactAlarms:   ${h.canScheduleExactAlarms}`,
    `batteryExempt: ${h.ignoreBatteryOptimizations}`,
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export default function App() {
  const [status, setStatus] = useState<Status>({ kind: 'idle' });
  const [lastEvent, setLastEvent] = useState<AlarmEvent | null>(null);
  const unsubRef = useRef<(() => void) | null>(null);

  // Subscribe to alarm events on mount; unsubscribe on unmount.
  useEffect(() => {
    unsubRef.current = addAlarmEventListener((event) => {
      setLastEvent(event);
    });
    return () => {
      unsubRef.current?.();
    };
  }, []);

  function ok(msg: string) {
    setStatus({ kind: 'ok', message: msg });
  }
  function err(msg: string) {
    setStatus({ kind: 'error', message: msg });
  }

  async function handleScheduleLoop() {
    const result = await scheduleAlarm({
      id: 'demo',
      timestampMs: Date.now() + 10_000,
      title: 'Alarm',
      body: 'Ringing…',
      sound: 'demo', // Android: res/raw/demo.mp3 — iOS: demo.wav in app bundle
      loopSound: true,
      stopLabel: 'Stop',
    });
    result ? ok('Looping alarm scheduled (fires in 10 s)') : err('scheduleAlarm failed');
  }

  async function handleScheduleOneShot() {
    const result = await scheduleAlarm({
      id: 'demo-oneshot',
      timestampMs: Date.now() + 10_000,
      title: 'Alarm',
      body: 'One-shot ring',
      sound: 'demo',
      loopSound: false,
    });
    result ? ok('One-shot alarm scheduled (fires in 10 s)') : err('scheduleAlarm failed');
  }

  async function handleStop() {
    await stopSound();
    ok('stopSound() called');
  }

  async function handleCancelDemo() {
    const result = await cancelAlarm('demo');
    ok(`cancelAlarm('demo') → ${result}`);
  }

  async function handleCancelAll() {
    const result = await cancelAll();
    ok(`cancelAll() → ${result}`);
  }

  async function handleRequestPermission() {
    const granted = await requestPermissions();
    ok(`requestPermissions() → ${granted}`);
  }

  async function handleHealth() {
    const health = await getAlarmHealth();
    ok(formatHealth(health));
  }

  async function handleExactAlarmSettings() {
    await openExactAlarmSettings();
    ok('Opened exact-alarm settings');
  }

  async function handleBatterySettings() {
    await openBatteryOptimizationSettings();
    ok('Opened battery optimization settings');
  }

  async function handleNotifSettings() {
    await openNotificationSettings();
    ok('Opened notification settings');
  }

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  const statusColor =
    status.kind === 'ok'
      ? '#1a7a1a'
      : status.kind === 'error'
        ? '#b00020'
        : '#555';

  return (
    <SafeAreaView style={styles.safeArea}>
      <ScrollView contentContainerStyle={styles.scroll}>

        {/* Header */}
        <Text style={styles.header}>react-native-alarm</Text>
        <Text style={styles.subheader}>
          Available: {isAvailable() ? 'YES' : 'NO (web / missing native)'}
        </Text>
        <Text style={styles.subheader}>Platform: {Platform.OS}</Text>

        {/* Scheduling */}
        <Group name="Schedule">
          <Button title="Schedule in 10 s (loop)" onPress={handleScheduleLoop} />
          <Spacer />
          <Button title="Schedule one-shot in 10 s" onPress={handleScheduleOneShot} />
        </Group>

        {/* Playback control */}
        <Group name="Playback">
          <Button title="Stop sound" onPress={handleStop} />
          <Spacer />
          <Button title="Cancel demo alarm" onPress={handleCancelDemo} />
          <Spacer />
          <Button title="Cancel all alarms" onPress={handleCancelAll} />
        </Group>

        {/* Permissions */}
        <Group name="Permissions">
          <Button title="Request permissions" onPress={handleRequestPermission} />
        </Group>

        {/* Health check */}
        <Group name="Alarm Health">
          <Button title="Get alarm health" onPress={handleHealth} />
        </Group>

        {/* Settings */}
        <Group name="System Settings">
          <Button title="Open exact-alarm settings" onPress={handleExactAlarmSettings} />
          <Spacer />
          <Button title="Open battery optimization settings" onPress={handleBatterySettings} />
          <Spacer />
          <Button title="Open notification settings" onPress={handleNotifSettings} />
        </Group>

        {/* Status area */}
        <Group name="Status">
          <Text style={[styles.statusText, { color: statusColor }]}>
            {status.kind === 'idle' ? '(tap a button above)' : status.message}
          </Text>
        </Group>

        {/* Last alarm event */}
        <Group name="Last Alarm Event">
          {lastEvent ? (
            <View>
              <Row label="id" value={lastEvent.id} />
              <Row label="action" value={lastEvent.action} />
              {lastEvent.data && (
                <Row label="data" value={JSON.stringify(lastEvent.data)} />
              )}
            </View>
          ) : (
            <Text style={styles.statusText}>(no event yet)</Text>
          )}
        </Group>

        {/* Sound note */}
        <View style={styles.noteBox}>
          <Text style={styles.noteTitle}>Sound file note</Text>
          <Text style={styles.noteText}>
            The buttons above use sound: &apos;demo&apos;. You must add the audio file before
            running on device:
          </Text>
          <Text style={styles.noteCode}>
            Android: example/android/app/src/main/res/raw/demo.mp3
          </Text>
          <Text style={styles.noteCode}>
            iOS: demo.wav (or .caf) added to the Xcode app target bundle
          </Text>
          <Text style={styles.noteText}>
            Without the file the alarm fires silently (system default sound).
          </Text>
        </View>

      </ScrollView>
    </SafeAreaView>
  );
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

function Spacer() {
  return <View style={styles.spacer} />;
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.row}>
      <Text style={styles.rowLabel}>{label}:</Text>
      <Text style={styles.rowValue}>{value}</Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// Styles
// ---------------------------------------------------------------------------

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#f0f0f0',
  },
  scroll: {
    padding: 16,
    paddingBottom: 40,
  },
  header: {
    fontSize: 26,
    fontWeight: '700',
    marginBottom: 4,
    color: '#111',
  },
  subheader: {
    fontSize: 13,
    color: '#666',
    marginBottom: 4,
  },
  group: {
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 16,
    marginTop: 16,
    shadowColor: '#000',
    shadowOpacity: 0.06,
    shadowRadius: 4,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  groupHeader: {
    fontSize: 13,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
    color: '#888',
    marginBottom: 12,
  },
  spacer: {
    height: 8,
  },
  statusText: {
    fontSize: 14,
    lineHeight: 20,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  row: {
    flexDirection: 'row',
    marginBottom: 4,
    flexWrap: 'wrap',
  },
  rowLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#444',
    marginRight: 6,
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
  },
  rowValue: {
    fontSize: 13,
    color: '#222',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    flexShrink: 1,
  },
  noteBox: {
    backgroundColor: '#fffbe6',
    borderRadius: 10,
    padding: 16,
    marginTop: 16,
    borderWidth: 1,
    borderColor: '#ffe082',
  },
  noteTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#7a5c00',
    marginBottom: 6,
  },
  noteText: {
    fontSize: 12,
    color: '#7a5c00',
    marginBottom: 4,
  },
  noteCode: {
    fontSize: 11,
    color: '#5a3c00',
    fontFamily: Platform.OS === 'ios' ? 'Menlo' : 'monospace',
    marginBottom: 2,
    marginLeft: 8,
  },
});
