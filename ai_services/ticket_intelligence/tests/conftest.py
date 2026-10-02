"""Pytest configuration and fixture initialization."""
import os

# Prevent Windows OpenMP / runtime library conflicts between scikit-learn and onnxruntime
os.environ["KMP_DUPLICATE_LIB_OK"] = "TRUE"

try:
    import onnxruntime  # Pre-initialize runtime before scikit-learn
except Exception:
    pass
