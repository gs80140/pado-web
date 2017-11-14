echo off
@setlocal enabledelayedexpansion

@set STORE_PASSWORD=pado-web
echo password=%STORE_PASSWORD%

REM Drop the version postfix from the pado jar file names.
pushd lib
for %%i in (pado-*.jar) do (
   call:parseFileName %%i 2
)

REM Drop the version postif from the gemfire jar file names.
for %%i in (gemfire-*.jar) do (
   call:parseFileName %%i 1
)
popd

if not exist "slib\naf" (
  mkdir slib\naf
)
if not exist "slib\app" (
  mkdir slib\app
)

REM lib\*.jar
if exist "lib" (
   for %%i in (lib\*.jar) do (
      echo %%i   
      jarsigner -keystore pado-web.keystore -storepass %STORE_PASSWORD% -tsa http://timestamp.digicert.com -signedjar s%%i %%i pado-web
   )
)

REM lib\naf\*.jar
if exist "lib\naf" (
   for %%i in (lib\naf\*.jar) do (
      echo %%i
      jarsigner -keystore pado-web.keystore -storepass %STORE_PASSWORD% -tsa http://timestamp.digicert.com -signedjar s%%i %%i pado-web
   )
)

REM lib\app\*.jar
if exist "lib\app" (
   for %%i in (lib\app\*.jar) do (
      echo %%i
      jarsigner -keystore pado-web.keystore -storepass %STORE_PASSWORD% -tsa http://timestamp.digicert.com -signedjar s%%i %%i pado-web
   )
)

REM etc.jar
echo etc.jar
REM jar cvfm etc.jar MANIFEST.MF etc
jar cvf etc.jar etc
jarsigner -keystore pado-web.keystore -storepass %STORE_PASSWORD% -tsa http://timestamp.digicert.com -signedjar setc.jar etc.jar pado-web

REM sec.jar
echo sec.jar
REM jar cvfm sec.jar MANIFEST.MF security
jar cvf sec.jar security
jarsigner -keystore pado-web.keystore -storepass %STORE_PASSWORD% -tsa http://timestamp.digicert.com -signedjar ssec.jar sec.jar pado-web


goto stop

REM parseFileName parses file names found in the lib directory
REM to drop the version postfix from the select files names.
:parseFileName

   @set FILE_NAME=%~1
   @set DELIMITER_COUNT=%~2
   set _myvar=%FILE_NAME%
   set n=0
:FORLOOP
for /F "tokens=1* delims=-" %%A IN ("%_myvar%") DO (
    set vector[!n!]=%%A
    set _myvar=%%B
    @set /a n="!n!+1"
    if NOT "%_myvar%"=="" goto FORLOOP
)

@set /a LAST_INDEX="!n!-1"
@set /a FILE_HEAD_LAST_INDEX="!LAST_INDEX!-!DELIMITER_COUNT!"
@set FILE_HEAD=
for /l %%i in (0,1,%FILE_HEAD_LAST_INDEX%) do (
   if %%i == 0 (
      @set FILE_HEAD=!vector[%%i]!
   ) else (
      @set FILE_HEAD=!FILE_HEAD!-!vector[%%i]!
   )
)

REM @set /a FILE_TAIL_START_INDEX="!FILE_HEAD_LAST_INDEX!+1"
REM @set FILE_TAIL=
REM for /l %%i in (%FILE_TAIL_START_INDEX%,1,%LAST_INDEX%) do (
REM   echo [%%i]="!vector[%%i]!"
REM )

if "%FILE_HEAD%" neq "" (
   move /y %FILE_NAME% %FILE_HEAD%.jar
)
goto:eof

:stop
