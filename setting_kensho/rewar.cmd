@echo off

rem for���[�v�ŕϐ����m���Ɏg�����ߒx�����ϐ����g���Ă�
setlocal ENABLEDELAYEDEXPANSION

rem ��war�t�@�C����ǂݍ��ށi�ŏ��̂P�t�@�C������������I���j
cd 00_ORIGINAL
for %%F in (*.war) do (
	set file=%%F
	echo !file!��ǂݍ��݂܂����B

	echo !file!��W�J���Ă��܂��D�D�D
	rem __work�f�B���N�g����war��W�J�B�G���[�\��������邽�ߏ��׍H����
	rmdir /s /q __work > NUL 2>&1
	mkdir __work
	copy !file! __work\ > NUL 2>&1
	cd __work
	jar xvf !file! > NUL 2>&1
	del /Q /F !file!
	echo !file!�̓W�J���I�����܂����B

	rem �c�̂��Ƃ̃f�B���N�g�������[�v����war���\�z����
	cd ..\..\01_PROPERTY
	for /d %%D in (*) do (
		set dir=%%D
		echo [!dir!]��war���\�z���Ă��܂��D�D�D

		xcopy /E /I /Q /H /R /Y !dir! ..\00_ORIGINAL\__work

		rem �J�����g�f�B���N�g�����ړ����Ȃ���jar�K�w���������Ȃ�Ȃ��̂ōs���ċA���Ă���
		cd ..\00_ORIGINAL\__work
		jar cvf ..\..\!dir!.war * > NUL 2>&1
		cd ..\..\01_PROPERTY

		echo [!dir!]��war���\�z���܂����B
	)

	rem __work�f�B���N�g�����폜�B�G���[�\��������邽�ߏ��׍H����
	rmdir /s /q ..\00_ORIGINAL\__work > NUL 2>&1

	goto exit;
)
:exit

endlocal

pause
