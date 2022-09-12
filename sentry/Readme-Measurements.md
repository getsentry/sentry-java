Measurements
============

## example readings:

```
"measurements": {
	"cpu_busy_factor": {
		"value": 0.8391516804695129
	},
	"frames_frozen": {
		"value": 0.0
	},
	"mem_bytes_allocated": {
		"value": 2.3946512E7
	},
	"frames_total": {
		"value": 42.0
	},
	"cpu_elapsed_time_diff_ms": {
		"value": 50.0
	},
	"battery_drain": {
		"value": 0.0
	},
	"battery_drain_per_second": {
		"value": 0.0
	},
	"transaction_duration_ms": {
		"value": 35.750389099121094
	},
	"cpu_realtime_diff_ns": {
		"value": 3.5787248E7
	},
	"cpu_time_ratio": {
		"value": 1.3971456289291382
	},
	"cpu_busy_factor_alt": {
		"value": 0.08391517400741577
	},
	"mem_bytes_allocated_per_second": {
		"value": 6.69825216E8
	},
	"cpu_time_ms": {
		"value": 30.0
	},
	"cpu_ticks": {
		"value": 3.0
	},
	"frames_slow": {
		"value": 1.0
	}
},


cpu ticks: 3 (@10ms each = 30ms), duration 35.75ms ~~> 84%
realtime diff: 35ms, elapsed time diff: 50ms (??? how can it be more than tx duration?) ~~> 139% ???


"measurements": {
	"cpu_busy_factor": {
		"value": 0.45071548223495483
	},
	"frames_frozen": {
		"value": 0.0
	},
	"mem_bytes_allocated": {
		"value": 2.5194424E7
	},
	"frames_total": {
		"value": 37.0
	},
	"cpu_elapsed_time_diff_ms": {
		"value": 335.0
	},
	"battery_drain": {
		"value": 0.0
	},
	"battery_drain_per_second": {
		"value": 0.0
	},
	"transaction_duration_ms": {
		"value": 754.3561401367188
	},
	"cpu_realtime_diff_ns": {
		"value": 7.54449472E8
	},
	"cpu_time_ratio": {
		"value": 0.44403237104415894
	},
	"cpu_busy_factor_alt": {
		"value": 0.0450715497136116
	},
	"mem_bytes_allocated_per_second": {
		"value": 3.339858E7
	},
	"cpu_time_ms": {
		"value": 340.0
	},
	"cpu_ticks": {
		"value": 34.0
	},
	"frames_slow": {
		"value": 11.0
	}
},

34 ticks => 340ms, duration: 754 ~~> 45%
335ms elapsed vs 754 realtime ~~> 44%

"measurements": {
	"cpu_busy_factor": {
		"value": 1.604942798614502
	},
	"frames_frozen": {
		"value": 0.0
	},
	"mem_bytes_allocated": {
		"value": 3.3304912E7
	},
	"frames_total": {
		"value": 32.0
	},
	"cpu_elapsed_time_diff_ms": {
		"value": 49.0
	},
	"battery_drain": {
		"value": 0.0
	},
	"battery_drain_per_second": {
		"value": 0.0
	},
	"transaction_duration_ms": {
		"value": 37.3845100402832
	},
	"cpu_realtime_diff_ns": {
		"value": 3.7429248E7
	},
	"cpu_time_ratio": {
		"value": 1.3091366291046143
	},
	"cpu_busy_factor_alt": {
		"value": 0.16049428284168243
	},
	"mem_bytes_allocated_per_second": {
		"value": 8.90874624E8
	},
	"cpu_time_ms": {
		"value": 60.0
	},
	"cpu_ticks": {
		"value": 6.0
	},
	"frames_slow": {
		"value": 5.0
	}
},
```

## Memory

```
2022-09-12 15:46:36.165 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,251,647,488/2,061,529,088 | 22,454,792/29,072,624|201,326,592
2022-09-12 15:46:49.180 19602-19602/io.sentry.samples.android I/Sentry: from background at end count 1
2022-09-12 15:46:49.180 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,117,208,576/2,061,529,088 | 2,380,599/8,791,951|201,326,592

...

2022-09-12 15:46:49.222 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,119,535,104/2,061,529,088 | 2,117,575/8,791,951|201,326,592
2022-09-12 15:47:43.159 19602-19602/io.sentry.samples.android I/Sentry: from background at end count 2
2022-09-12 15:47:43.160 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,382,686,720/2,061,529,088 | 3,475,317/8,500,653|201,326,592

...
2022-09-12 15:47:43.160 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,386,016,768/2,061,529,088 | 3,262,213/8,500,653|201,326,592
2022-09-12 15:47:43.708 19602-19602/io.sentry.samples.android I/Sentry: from background at end count 12
2022-09-12 15:47:43.708 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,386,016,768/2,061,529,088 | 3,262,213/8,500,653|201,326,592
2022-09-12 15:47:43.708 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,384,718,336/2,061,529,088 | 3,054,141/8,500,653|201,326,592
2022-09-12 15:47:43.709 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,385,742,336/2,061,529,088 | 2,594,629/8,500,653|201,326,592
2022-09-12 15:47:43.709 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,385,721,856/2,061,529,088 | 2,578,245/8,500,653|201,326,592
2022-09-12 15:47:43.709 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,385,721,856/2,061,529,088 | 2,561,861/8,500,653|201,326,592
2022-09-12 15:47:43.709 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,385,721,856/2,061,529,088 | 2,545,477/8,500,653|201,326,592
2022-09-12 15:47:43.710 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,386,143,744/2,061,529,088 | 2,496,229/8,500,653|201,326,592
2022-09-12 15:47:43.710 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,386,143,744/2,061,529,088 | 2,496,229/8,500,653|201,326,592
2022-09-12 15:47:43.710 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,385,885,696/2,061,529,088 | 2,479,845/8,500,653|201,326,592
2022-09-12 15:47:43.710 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,385,885,696/2,061,529,088 | 2,463,461/8,500,653|201,326,592
2022-09-12 15:47:43.710 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,386,143,744/2,061,529,088 | 2,463,461/8,500,653|201,326,592
2022-09-12 15:47:43.711 19602-19602/io.sentry.samples.android I/Sentry: from background at end 1,386,143,744/2,061,529,088 | 2,447,077/8,500,653|201,326,592
```
