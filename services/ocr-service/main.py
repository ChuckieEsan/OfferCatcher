"""EasyOCR 文字识别微服务

提供图片文字识别的 HTTP API，基于 EasyOCR。
对应 Python: app/infrastructure/adapters/ocr_adapter.py

启动方式:
    pip install easyocr fastapi uvicorn pillow
    python main.py

环境变量:
    OCR_PORT: 服务端口，默认 8001
    OCR_LANGS: 语言列表（逗号分隔），默认 "ch_sim,en"
    OCR_GPU: 是否使用 GPU，默认 true
"""

import base64
import os
import tempfile
from pathlib import Path
from contextlib import asynccontextmanager

import easyocr
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional
from urllib.request import urlopen


class ImageSource(BaseModel):
    """单张图片源（文件路径 / URL / Base64）"""
    source: str = Field(..., description="图片源：本地文件路径、http(s) URL 或 data:image/xxx;base64,...")


class OCRRequest(BaseModel):
    """OCR 识别请求"""
    images: List[ImageSource] = Field(..., description="图片源列表")


class OCRResponse(BaseModel):
    """OCR 识别响应"""
    text: str = Field(..., description="识别出的完整文本")
    line_count: int = Field(0, description="识别行数")


reader = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global reader
    langs = os.getenv("OCR_LANGS", "ch_sim,en").split(",")
    langs = [l.strip() for l in langs]
    use_gpu = os.getenv("OCR_GPU", "true").lower() == "true"
    reader = easyocr.Reader(langs, gpu=use_gpu, verbose=False)
    print(f"EasyOCR initialized: langs={langs}, gpu={use_gpu}")
    yield
    reader = None


app = FastAPI(title="OCR Service", version="1.0", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok"}


@app.post("/ocr", response_model=OCRResponse)
async def recognize(request: OCRRequest):
    """识别图片中的文字"""
    if reader is None:
        raise HTTPException(status_code=503, detail="OCR model not loaded")

    all_texts = []
    total_lines = 0

    for i, img_src in enumerate(request.images):
        temp_path = None
        try:
            image_path = _normalize_source(img_src.source)

            # Handle webp conversion
            if image_path.suffix.lower() == ".webp":
                from PIL import Image
                with Image.open(image_path) as img:
                    if img.mode in ("RGBA", "LA", "P"):
                        img = img.convert("RGB")
                    with tempfile.NamedTemporaryFile(delete=False, suffix=".png") as tmp:
                        img.save(tmp, "PNG")
                        temp_path = tmp.name
                    image_path = Path(temp_path)

            result = reader.readtext(str(image_path))
            lines = [detection[1] for detection in result]
            if lines:
                all_texts.append(f"--- 第 {i + 1} 张图片 ---")
                all_texts.append("\n".join(lines))
                total_lines += len(lines)

        except Exception as e:
            raise HTTPException(status_code=400, detail=f"Image {i + 1} failed: {e}")
        finally:
            if temp_path and Path(temp_path).exists():
                Path(temp_path).unlink()

    full_text = "\n\n".join(all_texts)
    return OCRResponse(text=full_text, line_count=total_lines)


def _normalize_source(source: str) -> Path:
    """将图片源标准化为本地文件路径"""
    # Local file
    if not source.startswith("http") and not source.startswith("data:"):
        path = Path(source)
        if path.exists():
            return path
        raise ValueError(f"Image file not found: {source}")

    # Base64
    if source.startswith("data:image"):
        if "," in source:
            base64_data = source.split(",", 1)[1]
        else:
            raise ValueError("Invalid Base64 image format")

        suffix = ".jpg"
        if "png" in source:
            suffix = ".png"
        elif "webp" in source:
            suffix = ".webp"
        elif "gif" in source:
            suffix = ".gif"

        image_data = base64.b64decode(base64_data)
        tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
        tmp.write(image_data)
        tmp.close()
        return Path(tmp.name)

    # URL
    if source.startswith("http://") or source.startswith("https://"):
        try:
            with urlopen(source) as response:
                image_data = response.read()
            content_type = response.headers.get("Content-Type", "image/jpeg")
            suffix = ".jpg"
            if "png" in content_type:
                suffix = ".png"
            elif "webp" in content_type:
                suffix = ".webp"
            elif "gif" in content_type:
                suffix = ".gif"

            tmp = tempfile.NamedTemporaryFile(delete=False, suffix=suffix)
            tmp.write(image_data)
            tmp.close()
            return Path(tmp.name)
        except Exception as e:
            raise ValueError(f"Failed to download image: {e}")

    raise ValueError(f"Invalid image source format")


if __name__ == "__main__":
    import uvicorn
    port = int(os.getenv("OCR_PORT", "8001"))
    uvicorn.run(app, host="0.0.0.0", port=port)
