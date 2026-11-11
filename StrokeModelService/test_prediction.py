from pprint import pprint

from app.dto import Sex, StrokeScreeningRequest
from app.stroke_model_service import StrokeModelService


def main():
    model_service = StrokeModelService()

    request = StrokeScreeningRequest(
        hypertension=True,
        high_cholesterol=True,
        myocardial_infarction_history=False,
        angina_or_chd_history=True,
        diabetes=True,
        sex=Sex.FEMALE,
        age=68,
        bmi=31.5,
        current_smoker=True
    )

    response = model_service.predict(request)

    print("Dữ liệu kiểm tra:")
    pprint(request.model_dump())

    print("\nKết quả sàng lọc:")
    pprint(response.model_dump())


if __name__ == "__main__":
    main()