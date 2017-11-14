@echo off
@set DEFAULT_URL=http://localhost:8080

if "%1" == "-?" (
   echo Usage:
   echo    desktop.bat [URL] [-?]
   echo.
   echo    Starts Pado Desktop via JNLP. A valid URL that has pado-web installed
   echo    is required.
   echo.
   echo       URL  Pado-web URL in the form of http://host:port.
   echo            If URL is not specified it defaults to %DEFAULT_URL%.
   echo. 
   echo    Default: ./desktop.sh %DEFAULT_URL%
   echo.
   goto stop
)

REM @set JAVAWS_TRACE_NATIVE=1
REM @set JAVAWS_VM_ARGS="-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,address=8989,server=y,suspend=n"

if  "%1" == "" (
   @set URL=%DEFAULT_URL%
) else (
   @set URL=%1
) 
@set URL=%URL%/pado-web/desktop/pado-desktop.jnlp

javaws %URL%

REM javaws -clearcache -viewer -wait %URL%

:stop
