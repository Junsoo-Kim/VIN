import pandas as pd
import os

# correlation이 저장된 파일 경로를 저장함
folder_path = os.path.join(os.path.dirname(__file__), 'correlation')

def customized_recommend_stocks(input_stock, final_score):
    file_path = os.path.join(folder_path, f"{input_stock}_corr.csv")
    correlation_data = pd.read_csv(file_path, index_col=0, encoding='utf-8')

    final_score = float(final_score)

    correlation_data_sorted = correlation_data.sort_values(by='Correlation') # 상관계수를 오름차순으로 정렬한다.
    corr_values = correlation_data_sorted['Correlation'].values # 상관계수를 배열로 저장한다.
    etf_list = correlation_data_sorted.index.values # ETF 종목 이름을 배열로 저장한다.

    # 고정으로 추천할 ETF 3개 ('공격적', '중립적', '보수적')
    etf_max = etf_list[corr_values.argmax()] # argmax()는 corr_values에서 값이 가장 큰 인덱스 값을 반환한다.
    etf_mid = etf_list[len(corr_values) // 2]
    etf_min = etf_list[corr_values.argmin()] # argmax()는 corr_values에서 값이 가장 작은 인덱스 값을 반환한다.

    fixed_recommendations = {
        "보수적 성향 (리스크 헷징 효과가 큰 종목)": [str(etf_min), float(corr_values.min())],
        "공격적 성향 (리스크 헷징 효과가 약한 종목)": [str(etf_max), float(corr_values.max())],
        "중립적 성향 (중립적인 투자에 적합한 종목)": [str(etf_mid), float(corr_values[len(corr_values) // 2])]
    }

    # 고정으로 추천할 ETF를 리스트에서 제거한다.
    remaining = correlation_data_sorted.drop(index=[etf_max, etf_mid, etf_min], errors='ignore')
    etf_list = remaining.index.values
    corr_values = remaining['Correlation'].values

    # user의 투자 성향을 기반으로 추천할 ETF 2개
    ratio = final_score / 100
    last_index = len(corr_values) - 1
    mid_index = len(corr_values) // 2

    if final_score > 20:  # 공격적 투자자
        index = mid_index + int(ratio * (last_index - mid_index))
        picks = [index, max(index - 1, 0)]
    elif final_score < -20:  # 보수적 투자자
        index = mid_index - int(abs(ratio) * mid_index)
        picks = [index, min(index + 1, last_index)]
    else:  # 중립적 투자자
        picks = [max(mid_index - 1, 0), min(mid_index + 1, last_index)]
    picks = list(dict.fromkeys(picks))

    dynamic_recommendations = {
        f"사용자 성향 추천 {n}": [str(etf_list[i]), float(corr_values[i])] for n, i in enumerate(picks, start=1)
    }

    # 최종적으로 추천할 ETF 리스트를 return
    return {**fixed_recommendations, **dynamic_recommendations}


if __name__ == "__main__":
    # 테스트:
    input_stock = 'AAXJ'
    recommendations = customized_recommend_stocks(input_stock, 50)
    print(recommendations)
