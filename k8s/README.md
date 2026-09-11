# Weather Data Hub Kubernetes Deployment

This directory contains single-node Kubernetes manifests for K3s.

## Public URLs

- Frontend: `http://60.205.211.104:30000/`
- Backend health: `http://60.205.211.104:30080/api/health`

## Apply

Run these commands on the server from `/opt/weather`:

```bash
docker-compose down

curl -sfL https://get.k3s.io | sh -
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml

docker save weather_backend:latest weather_frontend:latest weather_python_agent:latest mysql:8.4.9 redis:7.4-alpine postgis/postgis:16-3.5 -o /tmp/weather-images.tar
k3s ctr images import /tmp/weather-images.tar

kubectl apply -f k8s/00-namespace.yaml
kubectl -n weather create configmap mysql-init --from-file=deploy/mysql/init --dry-run=client -o yaml | kubectl apply -f -
kubectl -n weather create configmap postgis-init --from-file=deploy/postgis/init --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f k8s/
kubectl -n weather get pods -w
```
