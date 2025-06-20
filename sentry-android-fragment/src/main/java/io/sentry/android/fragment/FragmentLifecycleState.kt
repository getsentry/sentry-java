package io.sentry.android.fragment

public enum class FragmentLifecycleState(internal val breadcrumbName: String) {
  ATTACHED("attached"),
  SAVE_INSTANCE_STATE("save instance state"),
  CREATED("created"),
  VIEW_CREATED("view created"),
  STARTED("started"),
  RESUMED("resumed"),
  PAUSED("paused"),
  STOPPED("stopped"),
  VIEW_DESTROYED("view destroyed"),
  DESTROYED("destroyed"),
  DETACHED("detached");

  public companion object {
    public val states: Set<FragmentLifecycleState> =
      HashSet<FragmentLifecycleState>().apply {
        add(ATTACHED)
        add(SAVE_INSTANCE_STATE)
        add(CREATED)
        add(VIEW_CREATED)
        add(STARTED)
        add(RESUMED)
        add(PAUSED)
        add(STOPPED)
        add(VIEW_DESTROYED)
        add(DESTROYED)
        add(DETACHED)
      }
  }
}
