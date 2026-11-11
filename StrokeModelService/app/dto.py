from enum import Enum

from pydantic import BaseModel, ConfigDict, Field


class Sex(str, Enum):
    MALE = "MALE"
    FEMALE = "FEMALE"


class StrokeScreeningRequest(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid"
    )

    hypertension: bool = Field(
        description="Có cao huyết áp hay không"
    )

    high_cholesterol: bool = Field(
        alias="highCholesterol",
        description="Có cholesterol cao hay không"
    )

    myocardial_infarction_history: bool = Field(
        alias="myocardialInfarctionHistory",
        description="Có tiền sử nhồi máu cơ tim hay không"
    )

    angina_or_chd_history: bool = Field(
        alias="anginaOrChdHistory",
        description="Có tiền sử đau thắt ngực hoặc bệnh tim mạch vành"
    )

    diabetes: bool = Field(
        description="Có tiểu đường hay không"
    )

    sex: Sex = Field(
        description="Giới tính sinh học"
    )

    age: int = Field(
        ge=18,
        le=120,
        description="Tuổi hiện tại"
    )

    bmi: float = Field(
        ge=10,
        le=80,
        description="Chỉ số BMI"
    )

    current_smoker: bool = Field(
        alias="currentSmoker",
        description="Hiện tại có hút thuốc hay không"
    )


class StrokeScreeningResponse(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True
    )

    predicted_class: int = Field(
        alias="predictedClass"
    )

    model_score: float = Field(
        alias="modelScore"
    )

    screening_result: str = Field(
        alias="screeningResult"
    )

    recommendation: str

    disclaimer: str