import unittest
from pathlib import Path
from PIL import Image
import numpy as np


ROOT = Path(__file__).resolve().parents[1]
NEWS = ROOT / "resources/src/nurgling/hud/buttons/rbtn/news"
TEMPLATE = ROOT / "resources/src/nurgling/hud/buttons/rbtn/bud"


class NewsIconAssetsTest(unittest.TestCase):
    def test_all_states_keep_the_complete_native_frame(self):
        for state in ("u", "h", "d", "dh"):
            actual = Image.open(NEWS / f"{state}.res/image/image_0.png").convert("RGBA")
            template = Image.open(TEMPLATE / f"{state}.res/image/image_0.png").convert("RGBA")
            self.assertEqual((90, 90), actual.size)
            actual_pixels = np.asarray(actual)
            template_pixels = np.asarray(template)
            self.assertTrue(np.array_equal(actual_pixels[:, :, 3], template_pixels[:, :, 3]), state)
            border = np.ones((90, 90), dtype=bool)
            border[7:84, 7:84] = False
            self.assertTrue(np.array_equal(actual_pixels[border], template_pixels[border]), state)
            self.assertEqual((0, 0, 90, 90), actual.getchannel("A").getbbox(), state)
            # The frame must form a closed, opaque ring on all four sides.
            self.assertTrue(np.all(actual_pixels[5, 9:81, 3] > 240), state)
            self.assertTrue(np.all(actual_pixels[84, 9:81, 3] > 240), state)
            self.assertTrue(np.all(actual_pixels[9:81, 5, 3] > 240), state)
            self.assertTrue(np.all(actual_pixels[9:81, 84, 3] > 240), state)


if __name__ == "__main__":
    unittest.main()
