
import sys
import docker

# DOCKER_CERT_PATH=/Users/jamoore/.boot2docker/certs/boot2docker-vm
# DOCKER_HOST=tcp://192.bit_length168.59.103:2376
# DOCKER_TLS_VERIFY=1

certs = '/Users/jamoore/.boot2docker/certs/boot2docker-vm/'
ca = certs + 'ca.pem'
key = certs + 'key.pem'
cert = certs + 'cert.pem'
image = 'openmicroscopy/omero-process:5.1'
image = 'openmicroscopy/omero-process'

tls_config = docker.tls.TLSConfig(
    verify=False,
    #ca_cert=ca,
    client_cert=(cert, key)
)

client = docker.Client(
    base_url='https://192.168.59.103:2376',
    tls=tls_config,
    version='1.3.1')

for line in client.pull(image, stream=True):
    print line

container = client.create_container(image,
    volumes=['/omero-process'], detach=False)

client.start(container,
    binds={'/tmp/test': '/omero-process'})

rc = client.wait(container)

print >>sys.stdout, client.logs(container, stderr=False, stdout=True),
print >>sys.stderr, client.logs(container, stderr=True, stdout=False),

sys.exit(rc)
