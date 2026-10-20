import os
import pandas as pd
import time

# CSV 파일이 저장된 폴더 경로
folder_path = os.path.join(os.path.dirname(__file__), 'correlation')

def recommend_stocks(input_stock):
    # 입력된 종목의 CSV 파일 불러오기
    file_path = os.path.join(folder_path, f"{input_stock}_corr.csv")
    data = pd.read_csv(file_path, index_col=0)

    # 먼저 상관계수를 오름차순으로 정렬함
    sorted_data = data['Correlation'].sort_values()

    # 입력으로 들어온 종목과 큰 음의 상관관계를 가진 종목을 찾는다. (리스크  헷징 효과가 큰 종목)
    negative_corr_stock = sorted_data.idxmin()
    negative_corr_value = sorted_data.min()

    # 입력으로 들어온 종목과 가장 큰 양의 상관관계를 가진 종목을 찾는다. (리스크 헷징 효과가 약한 종목)
    positive_corr_stock = sorted_data.idxmax()
    positive_corr_value = sorted_data.max()

    # 입력으로 들어온 종목과 상관계수가 중간인 종목을 찾는다. (중립적인 투자자에게 적합)
    middle_index = len(sorted_data) // 2
    middle_corr_stock = sorted_data.index[middle_index]
    middle_corr_value = sorted_data.iloc[middle_index]

    return {
        '보수적 성향 (리스크 헷징 효과가 큰 종목)': [str(negative_corr_stock), float(negative_corr_value)],
        '공격적 성향 (리스크 헷징 효과가 약한 종목)': [str(positive_corr_stock), float(positive_corr_value)],
        '중립적 성향 (중립적인 투자에 적합한 종목)': [str(middle_corr_stock), float(middle_corr_value)]
    }


if __name__ == "__main__":
    start_time = time.time()

    # 테스트
    input_stock = 'AAXJ'  # 원하는 종목을 넣으면 된다.
    recommendations = recommend_stocks(input_stock)
    print(recommendations)

    end_time = time.time()
    print(f"Execution time: {end_time - start_time} seconds")
