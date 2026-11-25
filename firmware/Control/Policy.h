#pragma once
inline bool allowed(int n, int level, unsigned used, unsigned long elapsedMs, bool fresh) {
  return fresh && n >= 1 && n <= 3 && level >= n * 4 && used + n <= 12 && elapsedMs >= 10000;
}
