#pragma once
// Two consecutive near readings trigger one photo; three clear readings rearm it.
struct Presence {
  bool near = false;
  unsigned streak = 0;
  bool sample(int cm) {
    const bool qualifies = near ? (cm < 0 || cm >= 35) : (cm >= 2 && cm <= 25);
    streak = qualifies ? streak + 1 : 0;
    if (streak < (near ? 3u : 2u)) return false;
    streak = 0;
    near = !near;
    return near;
  }
};
inline bool canCycle(int level, unsigned daily, unsigned cycles) {
  return level >= 4 && daily < 12 && cycles < 3;
}
