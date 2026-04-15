from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from transformers import pipeline
from PIL import Image, ImageOps
import io, os, torch

app = FastAPI()

# ---- Config via env ----
MODEL_TASK = os.getenv("MODEL_TASK", "object-detection").strip()  # "object-detection" | "image-to-text"
MODEL_ID   = os.getenv("MODEL_ID",
                       "hustvl/yolos-tiny" if MODEL_TASK == "object-detection"
                       else "nlpconnect/vit-gpt2-image-captioning")

TORCH_THREADS = int(os.getenv("TORCH_NUM_THREADS", "2"))
torch.set_num_threads(TORCH_THREADS)

# ---- Pipeline ----
pipe = pipeline(MODEL_TASK, model=MODEL_ID, device=-1)

def _pil_from_upload(upload: UploadFile) -> Image.Image:
    b = upload.file.read() if hasattr(upload, "file") else None
    if not b:
        b = io.BytesIO(upload.file.read())
    img = Image.open(io.BytesIO(b if isinstance(b, (bytes, bytearray)) else upload))
    img = ImageOps.exif_transpose(img).convert("RGB")
    return img

@app.on_event("startup")
async def warm():
    # Warm with a tiny gray image
    dummy = Image.new("RGB", (64, 64), (127, 127, 127))
    _ = pipe(dummy)

@app.get("/healthz")
async def healthz():
    return {"status": "ok"}

@app.post("/detect")
async def detect(image: UploadFile = File(...)):
    try:
        pil = _pil_from_upload(image)
        image_width, image_height = pil.size
        out = pipe(pil)

        if MODEL_TASK == "object-detection":
            # Normalize to your current JSON schema
            results = []
            for o in out:
                box = o.get("box", {})  # yolos returns {'xmin','ymin','xmax','ymax'} or {'x','y','w','h'} depending on version
                # Make a unified (left, top, right, bottom)
                if {"xmin","ymin","xmax","ymax"} <= set(box.keys()):
                    left, top, right, bottom = box["xmin"], box["ymin"], box["xmax"], box["ymax"]
                elif {"x","y","w","h"} <= set(box.keys()):
                    left, top = box["x"], box["y"]
                    right, bottom = left + box["w"], top + box["h"]
                else:
                    # fallback if format is unexpected
                    left = int(o.get("left", 0)); top = int(o.get("top", 0))
                    right = int(o.get("right", 0)); bottom = int(o.get("bottom", 0))

                results.append({
                    "label":  o.get("label", "object"),
                    "score":  float(o.get("score", 0.0)),
                    "left":   int(left), "top": int(top),
                    "right":  int(right), "bottom": int(bottom),
                    "image_width": image_width,
                    "image_height": image_height,
                })
            return JSONResponse(results)

        elif MODEL_TASK == "image-to-text":
            # Standardize caption output
            # 'out' can be [{'generated_text': '...'}] or similar
            captions = []
            for o in out:
                captions.append(o.get("generated_text") or o.get("caption") or str(o))
            return JSONResponse({"captions": captions})

        else:
            return JSONResponse({"error": f"Unsupported MODEL_TASK={MODEL_TASK}"}, status_code=400)

    except Exception as e:
        return JSONResponse({"error": f"detect failed: {e}"}, status_code=500)
