package com.wjholden.nmap;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.ServerManagedPolicy;

class PipsLicensing extends Thread {

	private final byte[] SALT = new byte[] {
		44, 55, 67, -12, -12, 9, 2, 1, 33, 32,
		-46, 1, 23, -57, 3, -22, 2, 5, 77, -7
	};
	
	private final String BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuoLMeBwrgLi3y+ccrNcPuyXLTv3bwA1LujZDwS6gYDHZoQJly8NL8bbo/IZlc3cXLUwTsDpoiz1jFrIoQvMbfiB9tcTFDfsYJRqCS3WH6GXPZbuUEofTie+AQZB6AMzeRHpjRqlZGIWsYWXNrp9Q7kFYfiD2D8PMGiIZOjrNumrbucaiO5ptae+yHsQAmGNhggz3CBoFOdYfs/EbuTv7kimAi4WMaJvPANvedwUCzL27ux5BdUU5ETgsUhamkFDAXn8LWIZBUQNMFCGMBTw4x19sjDvfoX3SOxg/4PHMkQzgegK0oILFawbfUtiBT9eU8JGqgIQvsgcTBWa9jtJjywIDAQAB";
	
	private Context context;

	PipsLicensing(final Context context)
	{
		this.context = context;
	}
	
	public void run ()
	{
		LicenseChecker mChecker = new LicenseChecker(context,
				new ServerManagedPolicy(context,
						new AESObfuscator(SALT, context.getPackageName(), Settings.Secure.ANDROID_ID)),
				BASE64_PUBLIC_KEY);
		mChecker.checkAccess(new MyLicenseCheckerCallback());
	}

	private class MyLicenseCheckerCallback implements LicenseCheckerCallback {

		public void allow(int reason) {
			PipsError.log("License verification succeeded with reason: " + reason);
			MainActivity.handler.sendEmptyMessage(Constants.LICENSE_ALLOW);
		}

		public void dontAllow(int reason) {
			PipsError.log("License verification failed for reason: " + reason);
			
			gracePeriod(reason, Constants.LICENSE_FAIL);
		}

		public void applicationError(int errorCode) {
			PipsError.log("License verification encountered error, errorCode: " + errorCode);
			
			gracePeriod(errorCode, Constants.LICENSE_ERROR);
		}
		
		/**
		 * The end user gets a grace period of 24 hours if there is an error or license
		 * verification fails.
		 * License problems could be a component in many of the refunds I have seen in my Market account.
		 * We'll see how this works out. There is potential for abuse, but this is open source software anyways...
		 * @param reason The reason or errorCode provided to the dontAllow or applicationError methods.
		 * Not used, but recorded anyways for future use.
		 * @param handlerCode Code that will go to the MainActivity.handler if the user is outside their grace period
		 * (Constants.LICENSE_FAIL or Constants.LICENSE_ERROR).
		 */
		private void gracePeriod(final int reason, final int handlerCode)
		{
			MainActivity.handler.sendEmptyMessage(Constants.LICENSE_ERROR);
			SharedPreferences licensePreferences = context.getSharedPreferences("license", 0);
			long lastFailEpoch = licensePreferences.getLong("lastFailEpoch", Long.MAX_VALUE);
			long now = System.currentTimeMillis();
			PipsError.log("lastFailEpoch = " + lastFailEpoch + " now = " + now);
			if ((now - lastFailEpoch) > (1000 * 60 * 60 * 24)) 
			{
				MainActivity.handler.sendEmptyMessage(handlerCode);
			}
			else  // allow activity access for grace period.
			{
				MainActivity.handler.sendEmptyMessage(Constants.LICENSE_ALLOW);
				final SharedPreferences.Editor preferencesEditor = context.getSharedPreferences("license", 0).edit();
				preferencesEditor.clear();
				preferencesEditor.putLong("lastFailEpoch", now);
				preferencesEditor.putInt("reason", reason);
				preferencesEditor.commit(); // TODO once you get up to sdk 9 switch to apply().
			}
		}
	}
}
