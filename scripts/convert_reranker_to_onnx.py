"""
将 bge-reranker-base 从 PyTorch 转换为 ONNX 格式。

用法：
    pip install torch transformers onnx onnxruntime
    cd OfferCatcher && python scripts/convert_reranker_to_onnx.py
"""

import numpy as np
import os
import sys
import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

MODEL_DIR = "models/bge-reranker-base"
OUTPUT_PATH = os.path.join(MODEL_DIR, "model.onnx")

if not os.path.isdir(MODEL_DIR):
    print(f"错误：模型目录不存在 {MODEL_DIR}")
    sys.exit(1)

print(f"从 {MODEL_DIR} 加载模型...")
model = AutoModelForSequenceClassification.from_pretrained(MODEL_DIR)
model.eval()

tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)

# 用样例 (query, document) 对测试前向传播
query = "什么是HashMap"
doc = "HashMap是基于数组和链表的数据结构"
inputs = tokenizer(query, doc, return_tensors="pt", padding=True,
                   truncation=True, max_length=512)

print(f"  input_ids shape: {inputs['input_ids'].shape}")
print(f"  attention_mask shape: {inputs['attention_mask'].shape}")

with torch.no_grad():
    output = model(**inputs)
    print(f"  输出 shape: {output.logits.shape}")
    print(f"  输出值: {output.logits.item():.4f}")

# 导出 ONNX
print(f"\n导出到 {OUTPUT_PATH}...")
torch.onnx.export(
    model,
    (inputs["input_ids"], inputs["attention_mask"]),
    OUTPUT_PATH,
    input_names=["input_ids", "attention_mask"],
    output_names=["logits"],
    dynamic_axes={
        "input_ids": {0: "batch_size", 1: "sequence_length"},
        "attention_mask": {0: "batch_size", 1: "sequence_length"},
        "logits": {0: "batch_size"},
    },
    opset_version=18,
    do_constant_folding=True,
)

# 生成 tokenizer.json（DJL 要求此文件）
print("生成 tokenizer.json...")
tokenizer.save_pretrained(MODEL_DIR)

# 用 ONNX Runtime 验证
print("用 ONNX Runtime 验证...")
import onnxruntime as ort
session = ort.InferenceSession(OUTPUT_PATH)
onnx_out = session.run(None, {
    "input_ids": inputs["input_ids"].numpy(),
    "attention_mask": inputs["attention_mask"].numpy(),
})
print(f"  PyTorch  输出: {output.logits.item():.4f}")
print(f"  ONNX     输出: {onnx_out[0][0][0]:.4f}")
print(f"  一致性: {'通过' if np.allclose(onnx_out[0], output.logits.numpy(), atol=1e-4) else '失败'}")
print(f"\n模型已保存到 {OUTPUT_PATH}")
