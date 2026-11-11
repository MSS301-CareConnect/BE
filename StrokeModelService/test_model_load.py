from pprint import pprint

from app.stroke_model_service import StrokeModelService


def main():
    print("Đang đọc mô hình...")

    model_service = StrokeModelService()

    print("\nĐọc mô hình thành công.")

    print("\nLoại pipeline:")
    print(type(model_service.model))

    print("\nCác bước trong pipeline:")

    for step_name, step_value in model_service.model.steps:
        print(f"- {step_name}: {type(step_value).__name__}")

    print("\nThông tin mô hình:")
    pprint(model_service.get_model_information())


if __name__ == "__main__":
    main()