#include "../firmware/Control/Policy.h"
#include <cassert>
int main() {
  Presence p;
  assert(!p.sample(24) && !p.sample(30) && !p.sample(-1));
  assert(!p.sample(25) && p.sample(25) && p.near);
  assert(!p.sample(10) && !p.sample(32) && p.near);
  assert(!p.sample(40) && !p.sample(40) && !p.sample(40) && !p.near);
  assert(!p.sample(12) && p.sample(12));
  assert(canCycle(4,11,2));
  assert(!canCycle(-1,0,0) && !canCycle(3,0,0));
  assert(!canCycle(4,12,0) && !canCycle(4,0,3));
}
