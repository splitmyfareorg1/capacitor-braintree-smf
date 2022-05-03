import { WebPlugin } from '@capacitor/core';

import type {
  BraintreePlugin,
  DropInOptions,
  DropInResult,
  DropInToken,
  DataCollectorOptions,
  RecentMethod,
  AppleGoogleOptions
} from './definitions';

export class BraintreeWeb extends WebPlugin implements BraintreePlugin {
  setToken(options: DropInToken): Promise<any> {
    return this.setToken(options);
  }

  showDropIn(options: DropInOptions): Promise<DropInResult> {
    return this.showDropIn(options);
  }

  getDeviceData(options: DataCollectorOptions): Promise<any> {
    return this.getDeviceData(options);
  }

  getRecentMethods(options: DropInToken): Promise<RecentMethod> {
    return this.getRecentMethods(options);
  }

  showAppleGooglePay(options: AppleGoogleOptions): Promise<DropInResult> {
    return this.showAppleGooglePay(options);
  }
}
