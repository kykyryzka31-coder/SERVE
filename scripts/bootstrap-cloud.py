import urllib.request,hashlib,zipfile,pathlib,xml.etree.ElementTree as ET,os
# Required by platform-tools; sandboxed tasks may need a filesystem grant.
(pathlib.Path.home()/'.android').mkdir(exist_ok=True)
base=pathlib.Path(os.environ.get('ANDROID_TOOLS_DIR','/workspace/.android-tools'));base.mkdir(parents=True,exist_ok=True)
def fetch(url,path,digest,algorithm):
 if not path.exists(): urllib.request.urlretrieve(url,path)
 if hashlib.new(algorithm,path.read_bytes()).hexdigest()!=digest: raise RuntimeError('Checksum mismatch: '+str(path))
# Temurin JDK pinned to an official release, verified before extraction.
import tarfile
jdk_url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz'
jdk_archive=base/'temurin-21.0.13.tar.gz'
jdk_sha=urllib.request.urlopen(jdk_url+'.sha256.txt').read().decode().split()[0]
fetch(jdk_url,jdk_archive,jdk_sha,'sha256')
if not (base/'jdk-21.0.13+9/bin/javac').exists():
 with tarfile.open(jdk_archive) as t:t.extractall(base,filter='data')
os.environ['JAVA_HOME']=str(base/'jdk-21.0.13+9')
os.environ['PATH']=os.environ['JAVA_HOME']+'/bin:'+os.environ['PATH']
p=base/'gradle-8.13-bin.zip'
s=urllib.request.urlopen('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256').read().decode().strip()
fetch('https://downloads.gradle.org/distributions/gradle-8.13-bin.zip',p,s,'sha256')
with zipfile.ZipFile(p) as z:z.extractall(base)
(base/'gradle-8.13/bin/gradle').chmod(0o755)
root=ET.fromstring(urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml').read())
for package in root.findall('remotePackage'):
 if package.attrib['path']=='cmdline-tools;19.0':
  for a in package.findall('./archives/archive'):
   if a.findtext('host-os')=='linux':
    c=a.find('complete'); url=c.findtext('url'); checksum=c.findtext('checksum');print('SDK archive:',url,flush=True)
    p=base/url;fetch('https://dl.google.com/android/repository/'+url,p,checksum,'sha1')
    dest=base/'sdk/cmdline-tools';dest.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(p) as z:z.extractall(dest)
    
    if (dest/'19.0').exists():
     import shutil
     shutil.rmtree(dest/'cmdline-tools')
    else:
     (dest/'cmdline-tools').rename(dest/'19.0')
    for f in (dest/'19.0/bin').iterdir():f.chmod(0o755)
print('Tools installed',flush=True)

import subprocess,urllib.parse
for d in ['user-home','gradle-home']:(base/d).mkdir(exist_ok=True)
lines=[]
for proto in ['http','https']:
 u=urllib.parse.urlsplit(os.environ.get(proto.upper()+'_PROXY',''))
 if u.username or u.password: raise RuntimeError('Authenticated proxy requires separate secure Gradle configuration')
 if u.hostname:
  lines += ['systemProp.'+proto+'.proxyHost='+u.hostname,'systemProp.'+proto+'.proxyPort='+str(u.port or 80)]
# Use the platform-maintained Java trust store, including its proxy CA.
if pathlib.Path('/etc/ssl/certs/java/cacerts').is_file():
 lines.append('systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
# Only this script-owned file outside the checkout is regenerated.
(base/'gradle-home/gradle.properties').write_text('\n'.join(lines)+'\n')
env=os.environ.copy();env['ANDROID_USER_HOME']=str(base/'user-home')
manager=str(base/'sdk/cmdline-tools/19.0/bin/sdkmanager')
subprocess.run([manager,'--sdk_root='+str(base/'sdk'),'--licenses'],input='y\n'*100,text=True,env=env,check=True)
subprocess.run([manager,'--sdk_root='+str(base/'sdk'),'platform-tools','platforms;android-35','build-tools;35.0.0'],env=env,check=True)
