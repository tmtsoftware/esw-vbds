package vbds.client

import akka.stream.scaladsl.Flow
import akka.util.ByteString

// See https://stackoverflow.com/questions/50408617/how-to-buffer-and-drop-a-chunked-bytestring-with-a-delimiter/50488288#50488288
object TempTest {

  type ShouldDropTester = () => Boolean

  val dropEveryOther: ShouldDropTester =
    Iterator
      .from(1)
      .map(_ % 2 == 0)
      .next

  val endOfFile = ByteString("\n")

  val dropGroupPredicate: ShouldDropTester => ByteString => Boolean =
    (shouldDropTester) => {
      var dropGroup = shouldDropTester()

      (byteString) =>
        if (byteString equals endOfFile) {
          val returnValue = dropGroup
          dropGroup = shouldDropTester()
          returnValue
        } else {
          dropGroup
        }
    }

  val filterPredicateFunction: ByteString => Boolean =
    dropGroupPredicate(dropEveryOther)

  val dropGroups: Flow[ByteString, ByteString, _] =
  Flow[ByteString] filter filterPredicateFunction

}
