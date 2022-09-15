Measurements
============

## example readings:

```
"measurements": {
	"cpu_busy_factor": { // (# of ticks / ticks per second) / transactionDurationSeconds
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
	"cpu_elapsed_time_diff_ms": { // diff of Process.getElapsedCpuTime() end - start
		"value": 50.0
	},
	"battery_drain": {
		"value": 0.0
	},
	"battery_drain_per_second": {
		"value": 0.0
	},
	"transaction_duration_ms": { // tx.getTimestamp - tx.getStartTimestamp
		"value": 35.750389099121094
	},
	"cpu_realtime_diff_ns": { // diff of SystemClock.elapsedRealtimeNanos() end - start
		"value": 3.5787248E7
	},
	"cpu_time_ratio": { // cpu_elapsed_time_diff_ms * 1,000,000 / cpu_realtime_diff_ns
		"value": 1.3971456289291382
	},
	"cpu_busy_factor_alt": {
		"value": 0.08391517400741577
	},
	"mem_bytes_allocated_per_second": {
		"value": 6.69825216E8
	},
	"cpu_time_ms": { // (# of ticks / ticks per second) * 1000
		"value": 30.0
	},
	"cpu_ticks": { // cpu ticks in user mode + kernel mode + user mode of child processes + kernel mode of child processes
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

{
    		"cpu_busy_factor": {
    			"value": 0.5343509912490845
    		},
    		"mem_bytes_allocated": {
    			"value": 5579184.0
    		},
    		"cpu_elapsed_time_diff_ms": {
    			"value": 90.0
    		},
    		"battery_drain": {
    			"value": 0.0
    		},
    		"battery_drain_per_second": {
    			"value": 0.0
    		},
    		"transaction_duration_ms": {
    			"value": 262.0000915527344
    		},
    		"cpu_realtime_diff_ns": {
    			"value": 1.10549208E8
    		},
    		"cpu_time_ratio": {
    			"value": 0.8141170740127563
    		},
    		"cpu_busy_factor_alt": {
    			"value": 53.435096740722656
    		},
    		"mem_bytes_allocated_per_second": {
    			"value": 2.1294588E7
    		},
    		"cpu_time_ms": {
    			"value": 140.0
    		},
    		"app_start_cold": {
    			"value": 226.0
    		},
    		"cpu_ticks": {
    			"value": 14.0
    		}
    	},
    	
{
    "cpu_busy_factor": {
        "value": 0.0608651228249073
    },
    "frames_frozen": {
        "value": 1.0
    },
    "mem_bytes_allocated": {
        "value": 1.485382912E9
    },
    "frames_total": {
        "value": 45.0
    },
    "cpu_elapsed_time_diff_ms": {
        "value": 77381.0
    },
    "battery_drain": {
        "value": 0.0
    },
    "battery_drain_per_second": {
        "value": 0.0
    },
    "transaction_duration_ms": {
        "value": 74098.265625
    },
    "cpu_realtime_diff_ns": {
        "value": 7.4098352128E10
    },
    "cpu_time_ratio": {
        "value": 1.044301152229309
    },
    "cpu_busy_factor_alt": {
        "value": 6.086512088775635
    },
    "mem_bytes_allocated_per_second": {
        "value": 2.0046122E7
    },
    "cpu_time_ms": {
        "value": 4510.0
    },
    "cpu_ticks": {
        "value": 451.0
    },
    "frames_slow": {
        "value": 0.0
    }
    
    {
        "cpu_busy_factor": {
            "value": 0.06230438873171806
        },
        "frames_frozen": {
            "value": 2.0
        },
        "mem_bytes_allocated": {
            "value": 2.989691904E9
        },
        "frames_total": {
            "value": 79.0
        },
        "cpu_elapsed_time_diff_ms": {
            "value": 74433.0
        },
        "battery_drain": {
            "value": 0.0
        },
        "battery_drain_per_second": {
            "value": 0.0
        },
        "transaction_duration_ms": {
            "value": 71744.546875
        },
        "cpu_realtime_diff_ns": {
            "value": 7.1744610304E10
        },
        "cpu_time_ratio": {
            "value": 1.0374717712402344
        },
        "cpu_busy_factor_alt": {
            "value": 6.230438709259033
        },
        "mem_bytes_allocated_per_second": {
            "value": 4.1671348E7
        },
        "cpu_time_ms": {
            "value": 4470.0
        },
        "cpu_ticks": {
            "value": 447.0
        },
        "frames_slow": {
            "value": 28.0
        }
    }
    
{
    "cpu_busy_factor": {
        "value": 0.0274174977093935
    },
    "frames_frozen": {
        "value": 1.0
    },
    "mem_bytes_allocated": {
        "value": 5.113154048E9
    },
    "frames_total": {
        "value": 46.0
    },
    "cpu_elapsed_time_diff_ms": {
        "value": 285354.0
    },
    "battery_drain": {
        "value": 0.0
    },
    "battery_drain_per_second": {
        "value": 0.0
    },
    "transaction_duration_ms": {
        "value": 413969.21875
    },
    "cpu_realtime_diff_ns": {
        "value": 4.1396928512E11
    },
    "cpu_time_ratio": {
        "value": 0.6893120408058167
    },
    "cpu_busy_factor_alt": {
        "value": 2.7417497634887695
    },
    "mem_bytes_allocated_per_second": {
        "value": 1.2351533E7
    },
    "cpu_time_ms": {
        "value": 11350.0
    },
    "cpu_ticks": {
        "value": 1135.0
    },
    "frames_slow": {
        "value": 0.0
    }
},

{
    "cpu_busy_factor": {
        "value": 0.0018664085073396564
    },
    "frames_frozen": {
        "value": 1.0
    },
    "mem_bytes_allocated": {
        "value": 2.561356032E9
    },
    "frames_total": {
        "value": 41.0
    },
    "cpu_elapsed_time_diff_ms": {
        "value": 121691.0
    },
    "battery_drain": {
        "value": 0.0
    },
    "battery_drain_per_second": {
        "value": 0.0
    },
    "transaction_duration_ms": {
        "value": 326830.90625
    },
    "cpu_realtime_diff_ns": {
        "value": 3.2683098112E11
    },
    "cpu_time_ratio": {
        "value": 0.37233617901802063
    },
    "cpu_busy_factor_alt": {
        "value": 0.18664085865020752
    },
    "mem_bytes_allocated_per_second": {
        "value": 7836945.5
    },
    "cpu_time_ms": {
        "value": 610.0
    },
    "cpu_ticks": {
        "value": 61.0
    },
    "frames_slow": {
        "value": 11.0
    }
},

{
    "cpu_realtime_diff_ns": {
        "value": 3.03297298432E11
    },
    "frames_frozen": {
        "value": 1.0
    },
    "cpu_time_ratio": {
        "value": 0.0354701466858387
    },
    "mem_bytes_allocated": {
        "value": 1.83351136E8
    },
    "mem_bytes_allocated_per_second": {
        "value": 604526.25
    },
    "frames_total": {
        "value": 36.0
    },
    "cpu_elapsed_time_diff_ms": {
        "value": 10758.0
    },
    "frames_slow": {
        "value": 15.0
    }
},

{
    "cpu_busy_factor": {
        "value": 0.6345585584640503
    },
    "frames_frozen": {
        "value": 1.0
    },
    "mem_bytes_allocated": {
        "value": 2.487279872E9
    },
    "frames_total": {
        "value": 40.0
    },
    "cpu_elapsed_time_diff_ms": {
        "value": 91509.0
    },
    "battery_drain": {
        "value": 0.0
    },
    "battery_drain_per_second": {
        "value": 0.0
    },
    "transaction_duration_ms": {
        "value": 25182.861328125
    },
    "cpu_realtime_diff_ns": {
        "value": 2.518295552E10
    },
    "cpu_time_ratio": {
        "value": 3.6337673664093018
    },
    "cpu_busy_factor_alt": {
        "value": 63.45585250854492
    },
    "mem_bytes_allocated_per_second": {
        "value": 9.8768752E7
    },
    "cpu_time_ms": {
        "value": 15980.0
    },
    "cpu_ticks": {
        "value": 1598.0
    },
    "frames_slow": {
        "value": 9.0
    }
}

{
    "cpu_busy_factor": {
        "value": 2.5273425579071045
    },
    "frames_frozen": {
        "value": 1.0
    },
    "mem_bytes_allocated": {
        "value": 1.88301056E8
    },
    "frames_total": {
        "value": 42.0
    },
    "cpu_elapsed_time_diff_ms": {
        "value": 8800.0
    },
    "battery_drain": {
        "value": 0.0
    },
    "battery_drain_per_second": {
        "value": 0.0
    },
    "transaction_duration_ms": {
        "value": 3481.918212890625
    },
    "cpu_realtime_diff_ns": {
        "value": 3.482021888E9
    },
    "cpu_time_ratio": {
        "value": 2.5272672176361084
    },
    "cpu_busy_factor_alt": {
        "value": 252.73426818847656
    },
    "mem_bytes_allocated_per_second": {
        "value": 5.4079692E7
    },
    "cpu_time_ms": {
        "value": 8800.0
    },
    "cpu_ticks": {
        "value": 880.0
    },
    "frames_slow": {
        "value": 8.0
    }
}
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

1k strings
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end count 77
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,201,922,048/2,061,529,088 | runtime: 15,252,976/29,138,952|201,326,592 | pss: 88,999 | procstat: 180,220
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,201,156,096/2,061,529,088 | runtime: 15,170,592/29,138,952|201,326,592 | pss: 84,367 | procstat: 176,676
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,205,260,288/2,061,529,088 | runtime: 10,915,800/29,138,952|201,326,592 | pss: 91,626 | procstat: 183,036
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,207,242,752/2,061,529,088 | runtime: 8,212,440/29,138,952|201,326,592 | pss: 94,276 | procstat: 186,064
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,209,819,136/2,061,529,088 | runtime: 5,443,544/29,138,952|201,326,592 | pss: 96,836 | procstat: 188,708
2022-09-14 07:36:55.109 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,213,169,664/2,061,529,088 | runtime: 2,445,224/29,138,952|201,326,592 | pss: 100,192 | procstat: 191,356
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,216,262,144/2,061,529,088 | runtime: 0/30,478,432|201,326,592 | pss: 103,575 | procstat: 194,472
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,219,616,768/2,061,529,088 | runtime: 5,186,573/10,995,741|201,326,592 | pss: 105,867 | procstat: 197,840
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,524,672/2,061,529,088 | runtime: 2,892,813/10,995,741|201,326,592 | pss: 80,419 | procstat: 172,088
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,195,556,864/2,061,529,088 | runtime: 320,525/10,995,741|201,326,592 | pss: 82,655 | procstat: 174,456
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,808,448/2,061,529,088 | runtime: 4,220,885/10,995,741|201,326,592 | pss: 79,163 | procstat: 170,880
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,356,736/2,061,529,088 | runtime: 1,796,053/10,995,741|201,326,592 | pss: 81,539 | procstat: 173,240
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,196,679,168/2,061,529,088 | runtime: 0/11,673,672|201,326,592 | pss: 84,055 | procstat: 175,868
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,644,608/2,061,529,088 | runtime: 2,966,015/9,700,351|201,326,592 | pss: 78,947 | procstat: 170,504
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,618,880/2,061,529,088 | runtime: 426,495/9,700,351|201,326,592 | pss: 81,587 | procstat: 173,116
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,144,896/2,061,529,088 | runtime: 3,624,863/9,700,351|201,326,592 | pss: 78,595 | procstat: 170,264
2022-09-14 07:36:55.110 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,693,184/2,061,529,088 | runtime: 1,216,415/9,700,351|201,326,592 | pss: 80,991 | procstat: 172,632
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,196,015,616/2,061,529,088 | runtime: 4,465,102/9,487,262|201,326,592 | pss: 82,987 | procstat: 175,040
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,283,584/2,061,529,088 | runtime: 1,647,054/9,487,262|201,326,592 | pss: 79,839 | procstat: 171,400
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,195,089,920/2,061,529,088 | runtime: 4,258,310/9,487,262|201,326,592 | pss: 82,327 | procstat: 174,156
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,132,032/2,061,529,088 | runtime: 2,210,310/9,487,262|201,326,592 | pss: 79,823 | procstat: 171,492
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,195,454,464/2,061,529,088 | runtime: 0/9,914,776|201,326,592 | pss: 82,227 | procstat: 173,812
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,923,136/2,061,529,088 | runtime: 3,976,294/10,279,118|201,326,592 | pss: 78,239 | procstat: 170,008
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,029,056/2,061,529,088 | runtime: 2,124,902/10,279,118|201,326,592 | pss: 80,599 | procstat: 171,844
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,195,835,392/2,061,529,088 | runtime: 0/10,529,896|201,326,592 | pss: 82,851 | procstat: 174,596
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,119,744/2,061,529,088 | runtime: 3,160,734/10,279,118|201,326,592 | pss: 79,615 | procstat: 170,960
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,668,032/2,061,529,088 | runtime: 735,902/10,279,118|201,326,592 | pss: 82,047 | procstat: 173,856
2022-09-14 07:36:55.111 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,948,288/2,061,529,088 | runtime: 3,779,669/9,066,669|201,326,592 | pss: 77,679 | procstat: 169,360
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,238,528/2,061,529,088 | runtime: 830,549/9,066,669|201,326,592 | pss: 80,111 | procstat: 171,704
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,195,044,864/2,061,529,088 | runtime: 0/9,841,752|201,326,592 | pss: 82,171 | procstat: 173,672
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,648,704/2,061,529,088 | runtime: 1,655,629/9,066,669|201,326,592 | pss: 78,271 | procstat: 169,796
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,938,944/2,061,529,088 | runtime: 0/9,639,264|201,326,592 | pss: 80,539 | procstat: 171,956
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,583,744/2,061,529,088 | runtime: 3,288,901/9,789,069|201,326,592 | pss: 77,191 | procstat: 168,792
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,169,472/2,061,529,088 | runtime: 520,005/9,789,069|201,326,592 | pss: 79,591 | procstat: 171,160
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,330,368/2,061,529,088 | runtime: 3,546,917/9,789,069|201,326,592 | pss: 76,555 | procstat: 168,008
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,136,704/2,061,529,088 | runtime: 1,629,989/9,789,069|201,326,592 | pss: 79,031 | procstat: 170,620
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,684,992/2,061,529,088 | runtime: 4,013,181/9,500,925|201,326,592 | pss: 81,059 | procstat: 172,804
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,284,736/2,061,529,088 | runtime: 2,391,165/9,500,925|201,326,592 | pss: 77,919 | procstat: 169,392
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,091,072/2,061,529,088 | runtime: 130,173/9,500,925|201,326,592 | pss: 80,267 | procstat: 171,488
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,981,632/2,061,529,088 | runtime: 2,802,797/9,500,925|201,326,592 | pss: 77,119 | procstat: 168,712
2022-09-14 07:36:55.112 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,529,920/2,061,529,088 | runtime: 377,965/9,500,925|201,326,592 | pss: 79,627 | procstat: 171,612
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,303,488/2,061,529,088 | runtime: 5,189,558/10,608,494|201,326,592 | pss: 81,763 | procstat: 173,480
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,964,672/2,061,529,088 | runtime: 2,600,886/10,608,494|201,326,592 | pss: 78,331 | procstat: 170,012
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,771,008/2,061,529,088 | runtime: 208,822/10,608,494|201,326,592 | pss: 80,983 | procstat: 172,640
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,670,336/2,061,529,088 | runtime: 4,518,790/10,608,494|201,326,592 | pss: 76,787 | procstat: 168,428
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,861,696/2,061,529,088 | runtime: 2,028,422/10,608,494|201,326,592 | pss: 79,371 | procstat: 171,060
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,668,032/2,061,529,088 | runtime: 0/10,873,832|201,326,592 | pss: 81,991 | procstat: 173,440
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,043,072/2,061,529,088 | runtime: 3,409,029/10,160,397|201,326,592 | pss: 77,083 | procstat: 168,592
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,849,408/2,061,529,088 | runtime: 869,509/10,160,397|201,326,592 | pss: 79,611 | procstat: 171,220
2022-09-14 07:36:55.113 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,268,928/2,061,529,088 | runtime: 4,281,357/10,160,397|201,326,592 | pss: 76,651 | procstat: 168,456
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,108,032/2,061,529,088 | runtime: 1,135,629/10,160,397|201,326,592 | pss: 79,147 | procstat: 171,084
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,172,416/2,061,529,088 | runtime: 0/11,121,920|201,326,592 | pss: 81,815 | procstat: 173,484
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,510,592/2,061,529,088 | runtime: 2,671,125/9,765,933|201,326,592 | pss: 77,423 | procstat: 168,892
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,833,024/2,061,529,088 | runtime: 377,365/9,765,933|201,326,592 | pss: 79,979 | procstat: 171,268
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,494,208/2,061,529,088 | runtime: 3,468,605/9,765,933|201,326,592 | pss: 76,611 | procstat: 168,168
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,042,496/2,061,529,088 | runtime: 1,142,077/9,765,933|201,326,592 | pss: 79,051 | procstat: 171,072
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,590,784/2,061,529,088 | runtime: 3,443,357/9,180,477|201,326,592 | pss: 75,791 | procstat: 173,220
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,514,112/2,061,529,088 | runtime: 1,247,901/9,180,477|201,326,592 | pss: 78,087 | procstat: 169,844
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,193,062,400/2,061,529,088 | runtime: 4,332,229/9,180,477|201,326,592 | pss: 80,435 | procstat: 172,080
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,362,560/2,061,529,088 | runtime: 2,267,845/9,180,477|201,326,592 | pss: 77,739 | procstat: 169,484
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,910,848/2,061,529,088 | runtime: 858,821/9,180,477|201,326,592 | pss: 79,311 | procstat: 171,068
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,190,227,968/2,061,529,088 | runtime: 3,357,687/7,906,927|201,326,592 | pss: 76,025 | procstat: 167,504
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,329,792/2,061,529,088 | runtime: 3,077,799/7,906,927|201,326,592 | pss: 76,742 | procstat: 168,496
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,362,560/2,061,529,088 | runtime: 2,405,095/7,906,927|201,326,592 | pss: 77,120 | procstat: 168,988
2022-09-14 07:36:55.114 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,796,736/2,061,529,088 | runtime: 2,339,463/7,906,927|201,326,592 | pss: 77,478 | procstat: 169,316
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,689,664/2,061,529,088 | runtime: 2,306,695/7,906,927|201,326,592 | pss: 77,518 | procstat: 169,332
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,689,664/2,061,529,088 | runtime: 2,257,447/7,906,927|201,326,592 | pss: 77,558 | procstat: 169,332
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,689,664/2,061,529,088 | runtime: 2,224,679/7,906,927|201,326,592 | pss: 77,618 | procstat: 169,332
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,685,568/2,061,529,088 | runtime: 2,191,911/7,906,927|201,326,592 | pss: 77,650 | procstat: 169,340
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,685,568/2,061,529,088 | runtime: 2,159,143/7,906,927|201,326,592 | pss: 77,690 | procstat: 169,340
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,685,568/2,061,529,088 | runtime: 2,126,375/7,906,927|201,326,592 | pss: 77,746 | procstat: 169,340
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,685,568/2,061,529,088 | runtime: 2,093,607/7,906,927|201,326,592 | pss: 77,790 | procstat: 169,604
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,685,568/2,061,529,088 | runtime: 2,077,223/7,906,927|201,326,592 | pss: 77,822 | procstat: 169,604
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,955,904/2,061,529,088 | runtime: 2,044,455/7,906,927|201,326,592 | pss: 77,878 | procstat: 169,604
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,955,904/2,061,529,088 | runtime: 2,011,687/7,906,927|201,326,592 | pss: 77,918 | procstat: 169,604
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,955,904/2,061,529,088 | runtime: 1,831,463/7,906,927|201,326,592 | pss: 78,006 | procstat: 169,604
2022-09-14 07:36:55.115 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,192,955,904/2,061,529,088 | runtime: 1,798,695/7,906,927|201,326,592 | pss: 78,202 | procstat: 169,880
2022-09-14 07:36:56.464 3454-3454/io.sentry.samples.android I/Sentry: from background at end count 2
2022-09-14 07:36:56.464 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,191,239,680/2,061,529,088 | runtime: 3,570,246/9,411,566|201,326,592 | pss: 75,779 | procstat: 168,048
2022-09-14 07:36:56.464 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,078,208/2,061,529,088 | runtime: 3,324,358/9,411,566|201,326,592 | pss: 78,991 | procstat: 168,048
2022-09-14 07:36:56.522 3454-3454/io.sentry.samples.android I/Sentry: from background at end count 2
2022-09-14 07:36:56.522 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,194,078,208/2,061,529,088 | runtime: 3,324,358/9,411,566|201,326,592 | pss: 78,991 | procstat: 168,048
2022-09-14 07:36:56.522 3454-3454/io.sentry.samples.android I/Sentry: from background at end am: 1,195,266,048/2,061,529,088 | runtime: 2,880,686/9,411,566|201,326,592 | pss: 79,356 | procstat: 168,332

10k strings
2022-09-14 07:38:17.544 3622-3622/io.sentry.samples.android I/Sentry: from background at end count 85
2022-09-14 07:38:17.544 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,191,804,928/2,061,529,088 | runtime: 21,314,840/29,122,488|201,326,592 | pss: 74,606 | procstat: 170,688
2022-09-14 07:38:17.544 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,352,000/2,061,529,088 | runtime: 17,322,040/29,122,488|201,326,592 | pss: 78,899 | procstat: 171,696
2022-09-14 07:38:17.544 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,208,725,504/2,061,529,088 | runtime: 0/39,268,720|201,326,592 | pss: 101,690 | procstat: 192,508
2022-09-14 07:38:17.544 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,226,756,096/2,061,529,088 | runtime: 0/45,732,768|201,326,592 | pss: 118,646 | procstat: 211,052
2022-09-14 07:38:17.544 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,192,624,128/2,061,529,088 | runtime: 13,767,454/28,570,478|201,326,592 | pss: 85,004 | procstat: 177,432
2022-09-14 07:38:17.545 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,193,201,664/2,061,529,088 | runtime: 9,795,926/28,570,478|201,326,592 | pss: 87,970 | procstat: 180,824
2022-09-14 07:38:17.545 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,195,683,840/2,061,529,088 | runtime: 5,630,014/28,570,478|201,326,592 | pss: 91,538 | procstat: 184,256
2022-09-14 07:38:17.545 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,199,583,232/2,061,529,088 | runtime: 1,969,238/28,570,478|201,326,592 | pss: 94,770 | procstat: 187,424
2022-09-14 07:38:17.545 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,203,195,904/2,061,529,088 | runtime: 0/30,356,112|201,326,592 | pss: 98,677 | procstat: 191,384
2022-09-14 07:38:17.545 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,680,832/2,061,529,088 | runtime: 16,437,974/28,570,478|201,326,592 | pss: 81,365 | procstat: 174,248
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,189,834,752/2,061,529,088 | runtime: 10,987,230/28,570,478|201,326,592 | pss: 85,553 | procstat: 178,200
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,193,705,472/2,061,529,088 | runtime: 7,540,526/28,570,478|201,326,592 | pss: 89,297 | procstat: 182,068
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,196,802,048/2,061,529,088 | runtime: 3,660,630/28,570,478|201,326,592 | pss: 92,681 | procstat: 185,496
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,201,188,864/2,061,529,088 | runtime: 0/29,488,216|201,326,592 | pss: 96,701 | procstat: 188,808
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,203,769,344/2,061,529,088 | runtime: 6,947,141/16,517,469|201,326,592 | pss: 99,125 | procstat: 192,032
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,422,784/2,061,529,088 | runtime: 2,957,205/16,517,469|201,326,592 | pss: 81,945 | procstat: 174,684
2022-09-14 07:38:17.546 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,190,035,456/2,061,529,088 | runtime: 0/16,518,632|201,326,592 | pss: 86,225 | procstat: 178,376
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,177,600/2,061,529,088 | runtime: 5,786,365/16,517,469|201,326,592 | pss: 80,433 | procstat: 172,868
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,274,176/2,061,529,088 | runtime: 150,981/16,517,469|201,326,592 | pss: 84,417 | procstat: 177,076
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,192,140,800/2,061,529,088 | runtime: 7,924,237/15,937,485|201,326,592 | pss: 87,965 | procstat: 181,088
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,029,568/2,061,529,088 | runtime: 3,133,173/15,937,485|201,326,592 | pss: 81,365 | procstat: 173,908
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,189,384,192/2,061,529,088 | runtime: 8,319,573/15,937,485|201,326,592 | pss: 85,745 | procstat: 178,576
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,562,624/2,061,529,088 | runtime: 4,784,525/15,937,485|201,326,592 | pss: 80,837 | procstat: 173,584
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,659,200/2,061,529,088 | runtime: 629,669/15,937,485|201,326,592 | pss: 84,745 | procstat: 177,544
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,191,497,728/2,061,529,088 | runtime: 7,897,047/16,014,175|201,326,592 | pss: 87,293 | procstat: 180,000
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,771,520/2,061,529,088 | runtime: 3,275,551/16,014,175|201,326,592 | pss: 81,137 | procstat: 173,668
2022-09-14 07:38:17.547 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,868,096/2,061,529,088 | runtime: 8,704,959/16,014,175|201,326,592 | pss: 85,261 | procstat: 177,736
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,911,360/2,061,529,088 | runtime: 4,868,207/16,014,175|201,326,592 | pss: 80,265 | procstat: 173,048
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,187,491,840/2,061,529,088 | runtime: 806,327/16,014,175|201,326,592 | pss: 83,945 | procstat: 176,740
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,190,846,464/2,061,529,088 | runtime: 8,165,302/16,635,614|201,326,592 | pss: 78,909 | procstat: 179,796
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,853,440/2,061,529,088 | runtime: 4,344,934/16,635,614|201,326,592 | pss: 81,345 | procstat: 173,840
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,691,968/2,061,529,088 | runtime: 289,462/16,635,614|201,326,592 | pss: 84,933 | procstat: 177,264
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,473,088/2,061,529,088 | runtime: 6,968,942/16,635,614|201,326,592 | pss: 79,069 | procstat: 171,504
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,187,049,472/2,061,529,088 | runtime: 2,638,110/16,635,614|201,326,592 | pss: 83,001 | procstat: 175,980
2022-09-14 07:38:17.548 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,190,146,048/2,061,529,088 | runtime: 0/17,522,088|201,326,592 | pss: 86,093 | procstat: 179,196
2022-09-14 07:38:17.549 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,763,904/2,061,529,088 | runtime: 6,967,677/17,654,301|201,326,592 | pss: 79,757 | procstat: 172,492
2022-09-14 07:38:17.549 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,187,344,384/2,061,529,088 | runtime: 2,587,013/17,654,301|201,326,592 | pss: 83,729 | procstat: 176,436
2022-09-14 07:38:17.549 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,190,699,008/2,061,529,088 | runtime: 0/18,494,976|201,326,592 | pss: 87,145 | procstat: 179,888
2022-09-14 07:38:17.549 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,439,744/2,061,529,088 | runtime: 4,718,645/17,654,301|201,326,592 | pss: 81,513 | procstat: 174,052
2022-09-14 07:38:17.549 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,790,272/2,061,529,088 | runtime: 0/17,678,248|201,326,592 | pss: 85,313 | procstat: 178,012
2022-09-14 07:38:17.549 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,192,660,992/2,061,529,088 | runtime: 6,984,214/14,942,414|201,326,592 | pss: 88,561 | procstat: 181,488
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,595,392/2,061,529,088 | runtime: 1,945,894/14,942,414|201,326,592 | pss: 81,761 | procstat: 174,400
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,466,688/2,061,529,088 | runtime: 6,105,822/14,942,414|201,326,592 | pss: 78,229 | procstat: 177,932
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,013,760/2,061,529,088 | runtime: 3,356,214/14,942,414|201,326,592 | pss: 80,593 | procstat: 173,152
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,368,384/2,061,529,088 | runtime: 0/16,466,952|201,326,592 | pss: 84,801 | procstat: 176,916
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,183,465,472/2,061,529,088 | runtime: 5,911,813/16,300,557|201,326,592 | pss: 78,801 | procstat: 171,452
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,459,648/2,061,529,088 | runtime: 2,738,709/16,300,557|201,326,592 | pss: 82,213 | procstat: 175,128
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,189,556,224/2,061,529,088 | runtime: 7,671,629/16,300,557|201,326,592 | pss: 85,801 | procstat: 178,492
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,099,776/2,061,529,088 | runtime: 4,253,045/16,300,557|201,326,592 | pss: 80,845 | procstat: 173,740
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,970,496/2,061,529,088 | runtime: 0/17,481,872|201,326,592 | pss: 84,901 | procstat: 177,432
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,192,067,072/2,061,529,088 | runtime: 8,080,950/16,249,550|201,326,592 | pss: 87,597 | procstat: 180,324
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,980,416/2,061,529,088 | runtime: 2,659,006/16,249,550|201,326,592 | pss: 81,589 | procstat: 174,004
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,189,593,088/2,061,529,088 | runtime: 8,398,390/16,249,550|201,326,592 | pss: 85,333 | procstat: 178,548
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,239,040/2,061,529,088 | runtime: 4,875,094/16,249,550|201,326,592 | pss: 80,757 | procstat: 173,364
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,335,616/2,061,529,088 | runtime: 0/16,820,584|201,326,592 | pss: 84,717 | procstat: 177,312
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,178,176/2,061,529,088 | runtime: 7,828,950/16,258,622|201,326,592 | pss: 78,241 | procstat: 172,524
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,295,808/2,061,529,088 | runtime: 2,905,054/16,258,622|201,326,592 | pss: 82,049 | procstat: 174,680
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,189,650,432/2,061,529,088 | runtime: 8,575,766/16,258,622|201,326,592 | pss: 85,793 | procstat: 178,504
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,976,896/2,061,529,088 | runtime: 3,757,262/16,258,622|201,326,592 | pss: 80,921 | procstat: 173,340
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,188,847,616/2,061,529,088 | runtime: 0/17,116,656|201,326,592 | pss: 85,273 | procstat: 177,716
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,718,848/2,061,529,088 | runtime: 6,842,141/17,722,413|201,326,592 | pss: 79,213 | procstat: 172,020
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,187,704,832/2,061,529,088 | runtime: 2,429,389/17,722,413|201,326,592 | pss: 83,453 | procstat: 175,972
2022-09-14 07:38:17.550 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,190,801,408/2,061,529,088 | runtime: 0/18,996,368|201,326,592 | pss: 87,221 | procstat: 180,104
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,866,304/2,061,529,088 | runtime: 6,369,557/17,722,413|201,326,592 | pss: 80,513 | procstat: 173,224
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,187,221,504/2,061,529,088 | runtime: 1,676,797/17,722,413|201,326,592 | pss: 84,533 | procstat: 176,652
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,191,608,320/2,061,529,088 | runtime: 6,428,551/14,129,551|201,326,592 | pss: 87,573 | procstat: 180,676
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,184,284,672/2,061,529,088 | runtime: 3,678,943/14,129,551|201,326,592 | pss: 79,721 | procstat: 172,516
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,865,152/2,061,529,088 | runtime: 522,903/14,129,551|201,326,592 | pss: 82,429 | procstat: 175,156
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,182,990,336/2,061,529,088 | runtime: 7,286,751/14,129,551|201,326,592 | pss: 77,615 | procstat: 170,712
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,183,989,760/2,061,529,088 | runtime: 6,826,207/14,129,551|201,326,592 | pss: 78,496 | procstat: 171,880
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,529,856/2,061,529,088 | runtime: 6,202,607/14,129,551|201,326,592 | pss: 79,482 | procstat: 172,788
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,808,384/2,061,529,088 | runtime: 6,104,159/14,129,551|201,326,592 | pss: 79,294 | procstat: 172,576
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,062,336/2,061,529,088 | runtime: 6,071,391/14,129,551|201,326,592 | pss: 79,330 | procstat: 172,592
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,058,240/2,061,529,088 | runtime: 6,038,623/14,129,551|201,326,592 | pss: 79,370 | procstat: 172,736
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,058,240/2,061,529,088 | runtime: 6,005,855/14,129,551|201,326,592 | pss: 79,402 | procstat: 172,736
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,058,240/2,061,529,088 | runtime: 5,973,087/14,129,551|201,326,592 | pss: 79,446 | procstat: 172,736
2022-09-14 07:38:17.551 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,804,288/2,061,529,088 | runtime: 5,940,319/14,129,551|201,326,592 | pss: 79,478 | procstat: 172,736
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,185,800,192/2,061,529,088 | runtime: 5,891,167/14,129,551|201,326,592 | pss: 79,518 | procstat: 172,736
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,025,472/2,061,529,088 | runtime: 5,842,015/14,129,551|201,326,592 | pss: 79,570 | procstat: 172,736
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,025,472/2,061,529,088 | runtime: 5,792,767/14,129,551|201,326,592 | pss: 79,602 | procstat: 172,736
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,013,184/2,061,529,088 | runtime: 5,759,999/14,129,551|201,326,592 | pss: 79,634 | procstat: 172,736
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,013,184/2,061,529,088 | runtime: 5,743,615/14,129,551|201,326,592 | pss: 79,674 | procstat: 173,000
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,013,184/2,061,529,088 | runtime: 5,710,847/14,129,551|201,326,592 | pss: 79,714 | procstat: 173,000
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,017,280/2,061,529,088 | runtime: 5,678,079/14,129,551|201,326,592 | pss: 79,750 | procstat: 173,000
2022-09-14 07:38:17.552 3622-3622/io.sentry.samples.android I/Sentry: from background at end am: 1,186,017,280/2,061,529,088 | runtime: 5,579,775/14,129,551|201,326,592 | pss: 79,782 | procstat: 173,000

100k strings
2022-09-14 07:47:32.195 3998-3998/io.sentry.samples.android I/Sentry: from background at end count 100
2022-09-14 07:47:32.195 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,220,165,632/2,061,529,088 | runtime: 5,223,344/58,529,424|201,326,592 | pss: 119,160 | procstat: 208,772
2022-09-14 07:47:32.195 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,222,230,016/2,061,529,088 | runtime: 2,828,480/58,529,424|201,326,592 | pss: 121,176 | procstat: 210,884
2022-09-14 07:47:32.195 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,224,552,448/2,061,529,088 | runtime: 368,456/58,529,424|201,326,592 | pss: 123,788 | procstat: 213,524
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,204,166,656/2,061,529,088 | runtime: 24,034,320/56,200,808|201,326,592 | pss: 101,704 | procstat: 191,372
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,204,940,800/2,061,529,088 | runtime: 21,475,536/56,200,808|201,326,592 | pss: 103,240 | procstat: 193,028
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,579,200/2,061,529,088 | runtime: 18,540,280/56,200,808|201,326,592 | pss: 105,640 | procstat: 194,876
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,208,385,536/2,061,529,088 | runtime: 16,605,000/56,200,808|201,326,592 | pss: 107,660 | procstat: 196,988
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,966,016/2,061,529,088 | runtime: 13,505,056/56,200,808|201,326,592 | pss: 109,996 | procstat: 199,620
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,212,772,352/2,061,529,088 | runtime: 11,176,512/56,200,808|201,326,592 | pss: 112,192 | procstat: 201,980
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,215,352,832/2,061,529,088 | runtime: 8,600,952/56,200,808|201,326,592 | pss: 114,144 | procstat: 203,828
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,417,216/2,061,529,088 | runtime: 6,272,392/56,200,808|201,326,592 | pss: 116,396 | procstat: 206,204
2022-09-14 07:47:32.196 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,219,997,696/2,061,529,088 | runtime: 3,812,760/56,200,808|201,326,592 | pss: 118,652 | procstat: 208,316
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,221,804,032/2,061,529,088 | runtime: 1,417,848/56,200,808|201,326,592 | pss: 120,636 | procstat: 210,164
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,225,416,704/2,061,529,088 | runtime: 0/56,586,440|201,326,592 | pss: 124,220 | procstat: 213,320
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,321,152/2,061,529,088 | runtime: 21,834,344/56,200,808|201,326,592 | pss: 103,040 | procstat: 193,272
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,207,181,312/2,061,529,088 | runtime: 19,275,576/56,200,808|201,326,592 | pss: 105,268 | procstat: 194,752
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,208,729,600/2,061,529,088 | runtime: 17,241,600/56,200,808|201,326,592 | pss: 107,596 | procstat: 197,392
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,019,840/2,061,529,088 | runtime: 14,863,872/56,200,808|201,326,592 | pss: 109,068 | procstat: 198,976
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,212,342,272/2,061,529,088 | runtime: 12,387,448/56,200,808|201,326,592 | pss: 111,424 | procstat: 201,088
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,214,406,656/2,061,529,088 | runtime: 9,763,128/56,200,808|201,326,592 | pss: 113,412 | procstat: 203,200
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,471,040/2,061,529,088 | runtime: 7,614,824/56,200,808|201,326,592 | pss: 115,588 | procstat: 205,048
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,219,051,520/2,061,529,088 | runtime: 5,237,112/56,200,808|201,326,592 | pss: 117,948 | procstat: 207,160
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,221,115,904/2,061,529,088 | runtime: 2,120,376/56,200,808|201,326,592 | pss: 120,072 | procstat: 209,536
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,223,696,384/2,061,529,088 | runtime: 185,520/56,200,808|201,326,592 | pss: 122,676 | procstat: 212,156
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,665,216/2,061,529,088 | runtime: 21,844,728/56,200,808|201,326,592 | pss: 104,304 | procstat: 193,952
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,207,300,096/2,061,529,088 | runtime: 19,909,464/56,200,808|201,326,592 | pss: 105,320 | procstat: 195,168
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,208,590,336/2,061,529,088 | runtime: 17,039,744/56,200,808|201,326,592 | pss: 106,980 | procstat: 196,752
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,396,672/2,061,529,088 | runtime: 14,432,216/56,200,808|201,326,592 | pss: 109,336 | procstat: 198,864
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,212,719,104/2,061,529,088 | runtime: 11,938,984/56,200,808|201,326,592 | pss: 111,792 | procstat: 201,240
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,214,783,488/2,061,529,088 | runtime: 9,610,424/56,200,808|201,326,592 | pss: 113,960 | procstat: 203,876
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,622,016/2,061,529,088 | runtime: 6,707,528/56,200,808|201,326,592 | pss: 116,376 | procstat: 206,248
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,219,686,400/2,061,529,088 | runtime: 4,231,104/56,200,808|201,326,592 | pss: 118,536 | procstat: 208,344
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,222,008,832/2,061,529,088 | runtime: 1,639,616/56,200,808|201,326,592 | pss: 120,720 | procstat: 210,492
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,223,815,168/2,061,529,088 | runtime: 0/56,758,616|201,326,592 | pss: 122,360 | procstat: 212,060
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,227,943,936/2,061,529,088 | runtime: 0/58,644,664|201,326,592 | pss: 126,612 | procstat: 215,888
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,230,524,416/2,061,529,088 | runtime: 0/60,432,440|201,326,592 | pss: 128,840 | procstat: 218,920
2022-09-14 07:47:32.197 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,232,076,800/2,061,529,088 | runtime: 24,919,624/57,483,480|201,326,592 | pss: 129,228 | procstat: 219,024
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,784,000/2,061,529,088 | runtime: 22,475,560/57,483,480|201,326,592 | pss: 103,448 | procstat: 192,744
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,207,558,144/2,061,529,088 | runtime: 20,376,424/57,483,480|201,326,592 | pss: 105,496 | procstat: 194,592
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,208,590,336/2,061,529,088 | runtime: 17,703,328/57,483,480|201,326,592 | pss: 107,308 | procstat: 196,976
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,937,344/2,061,529,088 | runtime: 14,816,832/57,483,480|201,326,592 | pss: 109,692 | procstat: 199,352
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,212,997,632/2,061,529,088 | runtime: 12,684,912/57,483,480|201,326,592 | pss: 111,940 | procstat: 201,464
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,215,320,064/2,061,529,088 | runtime: 10,077,384/57,483,480|201,326,592 | pss: 114,308 | procstat: 204,100
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,384,448/2,061,529,088 | runtime: 7,453,112/57,483,480|201,326,592 | pss: 116,492 | procstat: 205,948
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,219,964,928/2,061,529,088 | runtime: 4,780,032/57,483,480|201,326,592 | pss: 118,752 | procstat: 208,588
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,222,029,312/2,061,529,088 | runtime: 2,352,712/57,483,480|201,326,592 | pss: 120,948 | procstat: 210,432
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,224,351,744/2,061,529,088 | runtime: 401,064/57,483,480|201,326,592 | pss: 123,832 | procstat: 212,544
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,204,224,000/2,061,529,088 | runtime: 23,690,088/56,474,920|201,326,592 | pss: 101,564 | procstat: 191,376
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,030,336/2,061,529,088 | runtime: 21,328,760/56,474,920|201,326,592 | pss: 103,736 | procstat: 193,436
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,207,578,624/2,061,529,088 | runtime: 18,606,120/56,474,920|201,326,592 | pss: 106,188 | procstat: 195,548
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,209,126,912/2,061,529,088 | runtime: 16,129,664/56,474,920|201,326,592 | pss: 108,280 | procstat: 197,924
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,211,731,968/2,061,529,088 | runtime: 13,292,336/56,474,920|201,326,592 | pss: 110,736 | procstat: 200,564
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,214,054,400/2,061,529,088 | runtime: 10,454,976/56,474,920|201,326,592 | pss: 112,928 | procstat: 202,672
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,376,832/2,061,529,088 | runtime: 7,748,736/56,474,920|201,326,592 | pss: 115,348 | procstat: 204,784
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,218,441,216/2,061,529,088 | runtime: 5,255,912/56,474,920|201,326,592 | pss: 117,488 | procstat: 206,896
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,221,021,696/2,061,529,088 | runtime: 2,533,712/56,474,920|201,326,592 | pss: 119,944 | procstat: 209,268
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,223,602,176/2,061,529,088 | runtime: 253,976/56,474,920|201,326,592 | pss: 122,264 | procstat: 211,644
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,227,472,896/2,061,529,088 | runtime: 24,229,232/56,474,920|201,326,592 | pss: 125,612 | procstat: 215,696
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,489,088/2,061,529,088 | runtime: 21,260,800/56,474,920|201,326,592 | pss: 104,012 | procstat: 193,436
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,207,779,328/2,061,529,088 | runtime: 18,587,720/56,474,920|201,326,592 | pss: 106,168 | procstat: 195,808
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,209,327,616/2,061,529,088 | runtime: 16,767,176/56,474,920|201,326,592 | pss: 108,592 | procstat: 198,180
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,211,392,000/2,061,529,088 | runtime: 14,536,136/56,474,920|201,326,592 | pss: 110,300 | procstat: 200,028
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,213,198,336/2,061,529,088 | runtime: 12,338,680/56,474,920|201,326,592 | pss: 112,200 | procstat: 201,876
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,215,520,768/2,061,529,088 | runtime: 10,058,896/56,474,920|201,326,592 | pss: 114,092 | procstat: 203,724
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,327,104/2,061,529,088 | runtime: 7,992,512/56,474,920|201,326,592 | pss: 115,888 | procstat: 205,828
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,219,391,488/2,061,529,088 | runtime: 6,139,656/56,474,920|201,326,592 | pss: 117,776 | procstat: 207,412
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,220,681,728/2,061,529,088 | runtime: 4,039,768/56,474,920|201,326,592 | pss: 119,240 | procstat: 208,996
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,222,488,064/2,061,529,088 | runtime: 2,055,776/56,474,920|201,326,592 | pss: 121,200 | procstat: 210,580
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,225,842,688/2,061,529,088 | runtime: 23,615,288/56,474,920|201,326,592 | pss: 124,108 | procstat: 213,720
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,771,712/2,061,529,088 | runtime: 22,090,096/56,474,920|201,326,592 | pss: 103,928 | procstat: 193,736
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,208,320,000/2,061,529,088 | runtime: 19,761,552/56,474,920|201,326,592 | pss: 105,956 | procstat: 195,584
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,384,384/2,061,529,088 | runtime: 17,448,984/56,474,920|201,326,592 | pss: 107,912 | procstat: 197,432
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,212,448,768/2,061,529,088 | runtime: 15,136,432/56,474,920|201,326,592 | pss: 109,844 | procstat: 199,808
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,214,513,152/2,061,529,088 | runtime: 12,906,192/56,474,920|201,326,592 | pss: 111,960 | procstat: 201,656
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,577,536/2,061,529,088 | runtime: 10,659,176/56,474,920|201,326,592 | pss: 113,880 | procstat: 203,504
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,218,383,872/2,061,529,088 | runtime: 8,281,056/56,474,920|201,326,592 | pss: 115,880 | procstat: 205,612
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,220,190,208/2,061,529,088 | runtime: 6,411,408/56,474,920|201,326,592 | pss: 117,520 | procstat: 207,196
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,221,738,496/2,061,529,088 | runtime: 4,508,960/56,474,920|201,326,592 | pss: 119,136 | procstat: 208,780
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,223,286,784/2,061,529,088 | runtime: 2,918,264/56,474,920|201,326,592 | pss: 120,632 | procstat: 210,100
2022-09-14 07:47:32.198 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,224,577,024/2,061,529,088 | runtime: 1,212,472/56,474,920|201,326,592 | pss: 121,992 | procstat: 211,684
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,206,513,664/2,061,529,088 | runtime: 22,871,960/56,474,920|201,326,592 | pss: 103,568 | procstat: 193,336
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,207,545,856/2,061,529,088 | runtime: 21,346,768/56,474,920|201,326,592 | pss: 105,016 | procstat: 194,588
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,208,852,480/2,061,529,088 | runtime: 19,608,144/56,474,920|201,326,592 | pss: 106,376 | procstat: 195,900
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,138,624/2,061,529,088 | runtime: 18,230,456/56,474,920|201,326,592 | pss: 107,640 | procstat: 197,228
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,210,912,768/2,061,529,088 | runtime: 17,262,824/56,474,920|201,326,592 | pss: 108,488 | procstat: 198,020
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,211,686,912/2,061,529,088 | runtime: 16,278,808/56,474,920|201,326,592 | pss: 109,336 | procstat: 198,812
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,212,719,104/2,061,529,088 | runtime: 15,242,168/56,474,920|201,326,592 | pss: 110,180 | procstat: 199,604
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,214,996,480/2,061,529,088 | runtime: 14,490,976/56,474,920|201,326,592 | pss: 111,686 | procstat: 201,404
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,215,266,816/2,061,529,088 | runtime: 14,310,432/56,474,920|201,326,592 | pss: 111,614 | procstat: 201,424
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,253,952/2,061,529,088 | runtime: 13,670,448/56,474,920|201,326,592 | pss: 112,514 | procstat: 202,268
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,286,720/2,061,529,088 | runtime: 13,637,680/56,474,920|201,326,592 | pss: 112,674 | procstat: 202,268
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,286,720/2,061,529,088 | runtime: 13,604,912/56,474,920|201,326,592 | pss: 112,718 | procstat: 202,268
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,216,286,720/2,061,529,088 | runtime: 13,555,760/56,474,920|201,326,592 | pss: 112,758 | procstat: 202,268
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,522,992/56,474,920|201,326,592 | pss: 112,806 | procstat: 202,284
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,490,224/56,474,920|201,326,592 | pss: 112,838 | procstat: 202,284
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,457,456/56,474,920|201,326,592 | pss: 112,870 | procstat: 202,284
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,424,688/56,474,920|201,326,592 | pss: 112,902 | procstat: 202,284
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,391,920/56,474,920|201,326,592 | pss: 112,930 | procstat: 202,548
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,359,152/56,474,920|201,326,592 | pss: 112,990 | procstat: 202,548
2022-09-14 07:47:32.199 3998-3998/io.sentry.samples.android I/Sentry: from background at end am: 1,217,052,672/2,061,529,088 | runtime: 13,326,384/56,474,920|201,326,592 | pss: 113,026 | procstat: 202,548


0 = "Name:\tsamples.android"
1 = "Umask:\t0077"
2 = "State:\tS (sleeping)"
3 = "Tgid:\t3012"
4 = "Ngid:\t0"
5 = "Pid:\t3012"
6 = "PPid:\t322"
7 = "TracerPid:\t3071"
8 = "Uid:\t10147\t10147\t10147\t10147"
9 = "Gid:\t10147\t10147\t10147\t10147"
10 = "FDSize:\t128"
11 = "Groups:\t3003 9997 20147 50147 "
12 = "VmPeak:\t15452248 kB"
13 = "VmSize:\t14864676 kB"
14 = "VmLck:\t       0 kB"
15 = "VmPin:\t       0 kB"
16 = "VmHWM:\t  137892 kB"
17 = "VmRSS:\t  137712 kB"
18 = "RssAnon:\t   71440 kB"
19 = "RssFile:\t   65408 kB"
20 = "RssShmem:\t     864 kB"
21 = "VmData:\t 1133208 kB"
22 = "VmStk:\t    8192 kB"
23 = "VmExe:\t       4 kB"
24 = "VmLib:\t  136308 kB"
25 = "VmPTE:\t    1040 kB"
26 = "VmSwap:\t       0 kB"
27 = "CoreDumping:\t0"
28 = "THP_enabled:\t1"
29 = "Threads:\t23"
30 = "SigQ:\t0/6684"
31 = "SigPnd:\t0000000000000000"
32 = "ShdPnd:\t0000000000000000"
33 = "SigBlk:\t0000000080001204"
34 = "SigIgn:\t0000000000000001"
35 = "SigCgt:\t0000006e400084f8"
36 = "CapInh:\t0000000000000000"
37 = "CapPrm:\t0000000000000000"
38 = "CapEff:\t0000000000000000"
39 = "CapBnd:\t0000000000000000"
40 = "CapAmb:\t0000000000000000"
41 = "NoNewPrivs:\t0"
42 = "Seccomp:\t2"
43 = "Seccomp_filters:\t1"
44 = "Speculation_Store_Bypass:\tthread vulnerable"
45 = "Cpus_allowed:\tf"
46 = "Cpus_allowed_list:\t0-3"
47 = "Mems_allowed:\t1"
48 = "Mems_allowed_list:\t0"
49 = "voluntary_ctxt_switches:\t139"
50 = "nonvoluntary_ctxt_switches:\t60"
```
