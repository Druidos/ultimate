//#Safe
/*
 * Reduced version of LexIndexValue-Pointer_true-termination.c
 * 
 * Author: heizmann@informatik.uni-freiburg.de
 * Date: 2014-08-02
 */

implementation main() returns ()
{
  var i : int;
  i := 0;
  while (true)
  {
    if (i < 1048) {
    assert 4 + i <= 1048;
    } else {
      break;
    }
    i := i + 4;
  }
}

procedure main() returns ();


