import React, { useState } from 'react';
import { Button, Grid, Paper, TextField } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { loginUser } from '../utils/loginUser';
import { SystemTypography } from '../styles/typography';
import { getEtfList } from '../utils/getEtfList';

const Signin = () => {
    const [id, setId] = useState('');
    const [password, setPassword] = useState('');
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();

        try {
            const response = await loginUser(id, password);
            if (!response.ok) {
                // 응답 상태 코드에 따라 다른 메시지 표시
                if (response.status === 401) {
                    alert('올바르지 않은 비밀번호입니다.');
                } else if (response.status === 404) {
                    alert('존재하지 않는 아이디입니다.');
                } else if (response.status === 409) {
                    alert('이미 로그인 중입니다.');
                } else {
                    alert('서버와의 통신 중 오류가 발생했습니다.');
                }
            } else {
                const responseJson = await response.json();
                document.cookie = `userId=${responseJson.userId}; path=/; max-age=7200;`;

                const etfListResponse = await getEtfList();
                const etfList = etfListResponse.data;
                localStorage.setItem('etfList', JSON.stringify(etfList)); // 로컬 스토리지에 저장
                alert('로그인 성공!');
                window.location.reload(); // 현재 위치가 '/'라면 새로고침
            }
        } catch (error) {
            console.error('로그인 중 에러 발생:', error);
            alert('로그인 실패!');
        }
    };

    return (
        <Grid item xs={6} style={{ padding: '20px', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            <Paper style={{ padding: '20px', maxWidth: '500px', width: '100%' }}>
                <SystemTypography variant="h4" align="center">
                    로그인
                </SystemTypography>
                <form onSubmit={handleLogin}>
                    <TextField
                        label="아이디"
                        fullWidth
                        margin="normal"
                        value={id}
                        onChange={(e) => setId(e.target.value)}
                        required
                    />
                    <TextField
                        label="비밀번호"
                        type="password"
                        fullWidth
                        margin="normal"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                    />
                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '20px' }}>
                        <Button type="submit" variant="contained" color="primary">
                            로그인
                        </Button>
                    </div>
                </form>
                <div style={{ marginTop: '20px', textAlign: 'center' }}>
                    <Button variant="text" onClick={() => navigate('/signup')}>
                        회원가입
                    </Button>
                </div>
            </Paper>
        </Grid>
    );
};

export default Signin;
