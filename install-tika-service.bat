prunsrv.exe //IS//TikaServer ^
--DisplayName="Tika Server" ^
--Description="Tika Server with custom parsers" ^
--Install="C:\Users\kenobi8\Downloads\commons-daemon-1.4.1-bin-windows\amd64\prunsrv.exe" ^
--Startup=manual ^
--LogPath="K:\tika\server-logs" ^
--Jvm="C:\program files\Java\jdk-23\bin\server\jvm.dll" ^
--Classpath="K:\tika\tika-server\tika-server-standard\target\tika-server-standard-4.0.0-SNAPSHOT.jar" ^
--StartMode=jvm ^
--StartClass=org.apache.tika.server.core.TikaServerCli ^
--StartMethod=main ^
--StopMode=jvm ^
--StopClass=org.apache.tika.server.core.TikaServerCli ^
--StopMethod=stop ^
++StartParams=-c#"k:\tika\tika-server-config-default.xml"