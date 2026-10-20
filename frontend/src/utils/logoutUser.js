import { getCookie } from './getCookie';

const LOGOUT_URL = process.env.REACT_APP_BACKEND_URL + '/user/signout';

export const logoutUser = async () => {
    const requestData = {
        currentCookie: getCookie('userId'),
    };

    try {
        const response = await fetch(LOGOUT_URL, {
            method: 'POST',
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(requestData),
        });

        if (response.status === 204) {
            document.cookie = 'userId=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
        }

        return response; // 로그아웃 결과를 반환
    } catch (error) {
        console.error('로그아웃 중 에러 발생:', error);
        throw error; // 에러를 다시 던져서 호출하는 쪽에서 처리할 수 있게 함
    }
};
