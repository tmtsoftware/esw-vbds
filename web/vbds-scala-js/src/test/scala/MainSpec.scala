import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MainSpec extends AnyWordSpec with Matchers with TypeCheckedTripleEquals {
	"tests" should {
		"work" in {
			55 should === (55)
		}
	}
}