### chocolatey 설치
> `> Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))`
> ** Windows PowerShell 관리자 모드로 실행 권장

### ffmepg 설치
> `> choco install ffmpeg`
> ** Windows PowerShell 관리자 모드로 실행 권장

### [application.properties] 내 아래 행 추가 
>`ffmpeg.path=C:/ProgramData/chocolatey/lib/ffmpeg/tools/ffmpeg/bin/ffmpeg.exe`
> ** 실제경로 확인 필요