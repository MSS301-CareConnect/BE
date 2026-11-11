import json
from pathlib import Path

import joblib
import pandas as pd

from app.dto import (
    Sex,
    StrokeScreeningRequest,
    StrokeScreeningResponse
)


class StrokeModelService:

    def __init__(self):
        project_directory = Path(__file__).resolve().parent.parent
        artifact_directory = project_directory / "artifacts"

        model_path = (
                artifact_directory
                / "stroke_screening_pipeline.joblib"
        )

        metadata_path = (
                artifact_directory
                / "stroke_screening_metadata.json"
        )

        metrics_path = (
                artifact_directory
                / "stroke_screening_test_metrics.json"
        )

        self._check_file_exists(model_path)
        self._check_file_exists(metadata_path)
        self._check_file_exists(metrics_path)

        self.model = joblib.load(model_path)
        self.metadata = self._read_json(metadata_path)
        self.metrics = self._read_json(metrics_path)

        self.feature_names = self.metadata["feature_names"]

    def predict(
            self,
            request: StrokeScreeningRequest
    ) -> StrokeScreeningResponse:

        feature_values = {
            "hypertension": int(request.hypertension),

            "high_cholesterol": int(
                request.high_cholesterol
            ),

            "myocardial_infarction_history": int(
                request.myocardial_infarction_history
            ),

            "angina_or_chd_history": int(
                request.angina_or_chd_history
            ),

            "diabetes": int(request.diabetes),

            "sex": self._convert_sex(request.sex),

            "age_group": self._convert_age_group(
                request.age
            ),

            "bmi_category": self._convert_bmi_category(
                request.bmi
            ),

            "current_smoker": int(
                request.current_smoker
            )
        }

        missing_features = set(
            self.feature_names
        ) - set(feature_values.keys())

        if missing_features:
            raise ValueError(
                "Thiếu đặc trưng của mô hình: "
                + ", ".join(sorted(missing_features))
            )

        ordered_features = {
            feature_name: feature_values[feature_name]
            for feature_name in self.feature_names
        }

        input_frame = pd.DataFrame(
            [ordered_features],
            columns=self.feature_names
        )

        predicted_class = int(
            self.model.predict(input_frame)[0]
        )

        classifier = self.model.named_steps["classifier"]
        class_values = list(classifier.classes_)

        positive_class_index = class_values.index(1)

        model_score = float(
            self.model.predict_proba(input_frame)[0][
                positive_class_index
            ]
        )

        return self._create_response(
            predicted_class=predicted_class,
            model_score=model_score
        )

    def get_model_information(self) -> dict:
        return {
            "model_name": self.metadata.get("model_name"),
            "model_version": self.metadata.get("model_version"),
            "algorithm": self.metadata.get("algorithm"),
            "dataset": self.metadata.get("dataset"),
            "target_column": self.metadata.get("target_column"),
            "feature_names": self.feature_names,
            "test_metrics": self.metrics
        }

    def _create_response(
            self,
            predicted_class: int,
            model_score: float
    ) -> StrokeScreeningResponse:

        if predicted_class == 1:
            screening_result = "NEEDS_FURTHER_ASSESSMENT"

            recommendation = (
                "Thông tin có nhiều đặc điểm tương đồng với nhóm "
                "có tiền sử đột quỵ trong dữ liệu nghiên cứu. "
                "Người dùng nên tham khảo nhân viên y tế để được "
                "đánh giá thêm."
            )
        else:
            screening_result = "LOWER_SCREENING_PATTERN"

            recommendation = (
                "Mô hình chưa phát hiện nhiều đặc điểm tương đồng "
                "với nhóm có tiền sử đột quỵ. Người dùng vẫn nên "
                "duy trì kiểm tra sức khỏe định kỳ."
            )

        return StrokeScreeningResponse(
            predicted_class=predicted_class,
            model_score=round(model_score, 6),
            screening_result=screening_result,
            recommendation=recommendation,
            disclaimer=(
                "Kết quả chỉ dùng để hỗ trợ sàng lọc và nghiên cứu. "
                "Đây không phải chẩn đoán y khoa và không thể hiện "
                "xác suất chắc chắn bị đột quỵ trong tương lai."
            )
        )

    def _convert_sex(self, sex: Sex) -> int:
        if sex == Sex.MALE:
            return 1

        return 2

    def _convert_age_group(self, age: int) -> int:
        if age <= 24:
            return 1
        if age <= 29:
            return 2
        if age <= 34:
            return 3
        if age <= 39:
            return 4
        if age <= 44:
            return 5
        if age <= 49:
            return 6
        if age <= 54:
            return 7
        if age <= 59:
            return 8
        if age <= 64:
            return 9
        if age <= 69:
            return 10
        if age <= 74:
            return 11
        if age <= 79:
            return 12

        return 13

    def _convert_bmi_category(self, bmi: float) -> int:
        if bmi < 18.5:
            return 1

        if bmi < 25:
            return 2

        if bmi < 30:
            return 3

        return 4

    def _read_json(self, file_path: Path) -> dict:
        with file_path.open(
                mode="r",
                encoding="utf-8"
        ) as file:
            return json.load(file)

    def _check_file_exists(self, file_path: Path) -> None:
        if not file_path.exists():
            raise FileNotFoundError(
                f"Không tìm thấy artifact: {file_path}"
            )