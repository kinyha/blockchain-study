{{/*
Expand the name of the chart.
*/}}
{{- define "blockchain-network.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this.
*/}}
{{- define "blockchain-network.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "blockchain-network.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "blockchain-network.labels" -}}
helm.sh/chart: {{ include "blockchain-network.chart" . }}
{{ include "blockchain-network.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "blockchain-network.selectorLabels" -}}
app.kubernetes.io/name: {{ include "blockchain-network.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the configmap
*/}}
{{- define "blockchain-network.configmapName" -}}
{{- printf "%s-config" (include "blockchain-network.fullname" .) }}
{{- end }}

{{/*
Node labels for specific node type
*/}}
{{- define "blockchain-network.nodeLabels" -}}
{{ include "blockchain-network.selectorLabels" . }}
app.kubernetes.io/component: {{ .nodeType }}
{{- end }}

{{/*
Peer seeds for blockchain nodes
*/}}
{{- define "blockchain-network.peerSeeds" -}}
{{- $fullname := include "blockchain-network.fullname" . -}}
{{- $port := .Values.service.port -}}
{{- $seeds := list -}}
{{- if .Values.nodes.miner.enabled -}}
{{- $seeds = append $seeds (printf "%s-miner:%d" $fullname (int $port)) -}}
{{- end -}}
{{- if .Values.nodes.wallet.enabled -}}
{{- $seeds = append $seeds (printf "%s-wallet:%d" $fullname (int $port)) -}}
{{- end -}}
{{- if .Values.nodes.fullNode.enabled -}}
{{- $seeds = append $seeds (printf "%s-fullnode:%d" $fullname (int $port)) -}}
{{- end -}}
{{- join "," $seeds -}}
{{- end }}
