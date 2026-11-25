#include "../firmware/Control/Policy.h"
#include <cassert>
int main(){
  assert(allowed(3,12,9,10000,true));
  assert(!allowed(1,-1,0,15000,true));
  assert(!allowed(0,70,0,15000,true));
  assert(!allowed(4,70,0,15000,true));
  assert(!allowed(2,70,11,15000,true));
  assert(!allowed(1,70,0,9999,true));
  assert(!allowed(1,70,0,15000,false));
}
