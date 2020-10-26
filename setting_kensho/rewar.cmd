@echo off

rem forループで変数を確実に使うため遅延環境変数を使ってる
setlocal ENABLEDELAYEDEXPANSION

rem 元warファイルを読み込む（最初の１ファイル処理したら終わり）
cd 00_ORIGINAL
for %%F in (*.war) do (
	set file=%%F
	echo !file!を読み込みました。

	echo !file!を展開しています．．．
	rem __workディレクトリにwarを展開。エラー表示を避けるため小細工あり
	rmdir /s /q __work > NUL 2>&1
	mkdir __work
	copy !file! __work\ > NUL 2>&1
	cd __work
	jar xvf !file! > NUL 2>&1
	del /Q /F !file!
	echo !file!の展開が終了しました。

	rem 団体ごとのディレクトリ分ループしてwarを構築する
	cd ..\..\01_PROPERTY
	for /d %%D in (*) do (
		set dir=%%D
		echo [!dir!]のwarを構築しています．．．

		xcopy /E /I /Q /H /R /Y !dir! ..\00_ORIGINAL\__work

		rem カレントディレクトリを移動しないとjar階層が正しくならないので行って帰ってする
		cd ..\00_ORIGINAL\__work
		jar cvf ..\..\!dir!.war * > NUL 2>&1
		cd ..\..\01_PROPERTY

		echo [!dir!]のwarを構築しました。
	)

	rem __workディレクトリを削除。エラー表示を避けるため小細工あり
	rmdir /s /q ..\00_ORIGINAL\__work > NUL 2>&1

	goto exit;
)
:exit

endlocal

pause
