./gradlew build
rsync build/libs/*.jar d:vkmirror/ --info=progress2
