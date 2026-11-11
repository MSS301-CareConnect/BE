from fastapi import FastAPI

from app.dto import (
    StrokeScreeningRequest,
    StrokeScreeningResponse
)
from app.stroke_model_service import StrokeModelService


app = FastAPI(
    title="CareConnect Stroke Model Service",
    version="1.0.0",
    description=(
        "Microservice sử dụng mô hình Logistic Regression kết hợp "
        "SMOTE để hỗ trợ sàng lọc đặc điểm liên quan đến tiền sử "
        "đột quỵ."
    )
)

stroke_model_service = StrokeModelService()


@app.get(
    "/health",
    tags=["Health"],
    summary="Kiểm tra trạng thái service"
)
def health_check():
    return {
        "status": "UP",
        "service": "StrokeModelService"
    }


@app.get(
    "/api/v1/stroke-screening/model-info",
    tags=["Stroke Screening"],
    summary="Xem thông tin mô hình"
)
def get_model_information():
    return stroke_model_service.get_model_information()


@app.post(
    "/api/v1/stroke-screening/predict",
    response_model=StrokeScreeningResponse,
    tags=["Stroke Screening"],
    summary="Thực hiện sàng lọc đột quỵ",
    description=(
            "Nhận thông tin sức khỏe cơ bản và trả về kết quả "
            "sàng lọc từ mô hình. Kết quả không thay thế chẩn đoán "
            "của nhân viên y tế."
    )
)
def predict_stroke_screening(
        request: StrokeScreeningRequest
):
    return stroke_model_service.predict(request)