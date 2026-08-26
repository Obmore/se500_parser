package se500;

final class Measurement {
    String imageName;
    String locationName;
    String featureName;
    float height;
    float area;
    float volume;
    float hUpFail, hUpWarn, hTarget, hLowWarn, hLowFail;
    float aUpFail, aUpWarn, aTarget, aLowWarn, aLowFail;
    float vUpFail, vUpWarn, vTarget, vLowWarn, vLowFail;
    boolean hasHeight;
    boolean hasArea;
    boolean hasVolume;
}
