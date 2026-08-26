java -jar nurgling_launcher.jar update https://raw.githubusercontent.com/Lanfir7/nurgling-release/stable/ ^
  -Dsun.java2d.uiScale.enabled=false ^
  -Xms512m -Xmx4g -Xss2m ^
  -XX:+UseG1GC ^
  -XX:SoftRefLRUPolicyMSPerMB=50 ^
  -XX:+UseStringDeduplication ^
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED ^
  -jar ./hafen.jar
pause