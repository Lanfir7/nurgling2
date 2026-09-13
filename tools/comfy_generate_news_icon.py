"""Generate the source artwork for the What's New HUD icon with local ComfyUI."""
import argparse
import json
import random
import time
import urllib.parse
import urllib.request
from pathlib import Path


def request_json(url, data=None):
    body = None if data is None else json.dumps(data).encode("utf-8")
    request = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--workflow", default=r"C:\Users\DENIS\Downloads\image_z_image_turbo.json")
    parser.add_argument("--host", default="http://127.0.0.1:8188")
    parser.add_argument("--output", default="build/news-icon-comfy.png")
    parser.add_argument("--seed", type=int, default=81420377613)
    args = parser.parse_args()

    workflow = json.loads(Path(args.workflow).read_text(encoding="utf-8"))
    workflow["57:27"]["inputs"]["text"] = (
        "single centered medieval game UI icon artwork, an unfurled aged parchment scroll "
        "with one small eight-point golden discovery star hovering above it, warm ochre, "
        "walnut brown and muted antique gold palette, hand-painted fantasy RPG inventory "
        "icon, tactile parchment texture, bold clean silhouette, soft directional light, "
        "isolated object, plain solid very dark neutral background, square composition, "
        "no border, no frame, no letters, no words, no typography, no modern symbols, "
        "no photorealistic scene, exactly one scroll and one star"
    )
    workflow["57:3"]["inputs"]["seed"] = args.seed or random.randrange(1 << 48)
    workflow["57:13"]["inputs"].update(width=1024, height=1024, batch_size=1)
    workflow["9"]["inputs"]["filename_prefix"] = "nurgling_news_icon"

    queued = request_json(args.host + "/prompt", {"prompt": workflow, "client_id": "codex-news-icon"})
    prompt_id = queued["prompt_id"]
    deadline = time.time() + 240
    while time.time() < deadline:
        history = request_json(args.host + "/history/" + prompt_id)
        record = history.get(prompt_id)
        if record:
            images = [image for output in record.get("outputs", {}).values() for image in output.get("images", [])]
            if images:
                image = images[0]
                query = urllib.parse.urlencode({"filename": image["filename"], "subfolder": image.get("subfolder", ""), "type": image.get("type", "output")})
                with urllib.request.urlopen(args.host + "/view?" + query, timeout=30) as response:
                    result = response.read()
                output = Path(args.output)
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_bytes(result)
                print(json.dumps({"prompt_id": prompt_id, "seed": workflow["57:3"]["inputs"]["seed"], "output": str(output)}))
                return
            status = record.get("status", {})
            if status.get("status_str") == "error":
                raise RuntimeError(json.dumps(status))
        time.sleep(2)
    raise TimeoutError(prompt_id)


if __name__ == "__main__":
    main()
